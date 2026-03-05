const { S3Client, RestoreObjectCommand, GetObjectCommand, HeadObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
const { DynamoDBClient, GetItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');

const s3 = new S3Client({ region: process.env.AWS_REGION });
const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });

const BUCKET = process.env.ARCHIVE_BUCKET;
const ITEMS_TABLE = process.env.ITEMS_TABLE;

function getUserId(event) {
  // Primary: Cognito Identity ID from API Gateway request context
  const cognitoId = event.requestContext?.identity?.cognitoIdentityId;
  if (cognitoId) return cognitoId;
  // Fallback: identity passed in header by the iOS app
  const headerIdentity = event.headers?.['x-identity-id'] || event.headers?.['X-Identity-Id'];
  if (headerIdentity) return headerIdentity;
  return null;
}

exports.handler = async (event) => {
  try {
    const userId = getUserId(event);
    if (!userId) { return response(400, { error: 'Missing user identity' }); }
    const body = JSON.parse(event.body);
    const { itemId } = body;

    if (!itemId) {
      return response(400, { error: 'Missing itemId' });
    }

    // Get item metadata
    const itemResult = await dynamodb.send(new GetItemCommand({
      TableName: ITEMS_TABLE,
      Key: {
        userId: { S: userId },
        itemId: { S: itemId },
      },
    }));

    if (!itemResult.Item) {
      return response(404, { error: 'Item not found' });
    }

    const s3Key = itemResult.Item.s3Key.S;
    const storageTier = itemResult.Item.storageTier.S;

    // For Glacier Instant Retrieval, generate presigned URL directly
    if (storageTier === 'instant') {
      const command = new GetObjectCommand({
        Bucket: BUCKET,
        Key: s3Key,
      });
      const downloadURL = await getSignedUrl(s3, command, { expiresIn: 86400 });

      return response(200, {
        status: 'available',
        downloadURL,
        estimatedWaitMinutes: 0,
      });
    }

    // For Flexible/Deep, check if already restored
    try {
      const headResult = await s3.send(new HeadObjectCommand({
        Bucket: BUCKET,
        Key: s3Key,
      }));

      if (headResult.Restore && headResult.Restore.includes('ongoing-request="false"')) {
        // Already restored, generate download URL
        const command = new GetObjectCommand({
          Bucket: BUCKET,
          Key: s3Key,
        });
        const downloadURL = await getSignedUrl(s3, command, { expiresIn: 86400 });

        await updateRetrievalStatus(userId, itemId, 'available', downloadURL);

        return response(200, {
          status: 'available',
          downloadURL,
          estimatedWaitMinutes: 0,
        });
      }

      if (headResult.Restore && headResult.Restore.includes('ongoing-request="true"')) {
        return response(200, {
          status: 'in_progress',
          downloadURL: null,
          estimatedWaitMinutes: storageTier === 'deep' ? 720 : 300,
        });
      }
    } catch (e) {
      // HeadObject might fail if not restored yet
    }

    // Initiate restore
    const tier = storageTier === 'deep' ? 'Bulk' : 'Standard';
    await s3.send(new RestoreObjectCommand({
      Bucket: BUCKET,
      Key: s3Key,
      RestoreRequest: {
        Days: 7,
        GlacierJobParameters: { Tier: tier },
      },
    }));

    await updateRetrievalStatus(userId, itemId, 'in_progress', null);

    return response(200, {
      status: 'initiating',
      downloadURL: null,
      estimatedWaitMinutes: storageTier === 'deep' ? 720 : 300,
    });
  } catch (error) {
    console.error('Error:', error);
    return response(500, { error: 'Internal server error' });
  }
};

async function updateRetrievalStatus(userId, itemId, status, downloadURL) {
  const updateExpr = downloadURL
    ? 'SET retrievalStatus = :status, retrievalURL = :url, retrievalExpiresAt = :expires'
    : 'SET retrievalStatus = :status';

  const values = {
    ':status': { S: status },
  };
  if (downloadURL) {
    values[':url'] = { S: downloadURL };
    values[':expires'] = { N: String(Date.now() + 86400000) };
  }

  await dynamodb.send(new UpdateItemCommand({
    TableName: ITEMS_TABLE,
    Key: {
      userId: { S: userId },
      itemId: { S: itemId },
    },
    UpdateExpression: updateExpr,
    ExpressionAttributeValues: values,
  }));
}

function response(statusCode, body) {
  return {
    statusCode,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
    body: JSON.stringify(body),
  };
}
