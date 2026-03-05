const { DynamoDBClient, QueryCommand } = require('@aws-sdk/client-dynamodb');

const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });
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
    const limit = parseInt(event.queryStringParameters?.limit || '50');
    const cursor = event.queryStringParameters?.cursor;

    const params = {
      TableName: ITEMS_TABLE,
      KeyConditionExpression: 'userId = :uid',
      ExpressionAttributeValues: {
        ':uid': { S: userId },
      },
      Limit: Math.min(limit, 100),
      ScanIndexForward: false,
    };

    if (cursor) {
      params.ExclusiveStartKey = JSON.parse(Buffer.from(cursor, 'base64').toString());
    }

    const result = await dynamodb.send(new QueryCommand(params));

    const items = (result.Items || []).map(item => ({
      id: item.itemId.S,
      originalAssetId: item.originalAssetId?.S || '',
      fileName: item.originalFilename.S,
      fileType: item.fileType.S,
      fileSize: parseInt(item.fileSize.N),
      storageTier: item.storageTier.S,
      archivedDate: new Date(parseInt(item.uploadedAt.N)).toISOString(),
      transferStatus: mapRetrievalStatus(item),
      metadata: {
        creationDate: item.creationDate?.S || null,
        pixelWidth: item.pixelWidth ? parseInt(item.pixelWidth.N) : null,
        pixelHeight: item.pixelHeight ? parseInt(item.pixelHeight.N) : null,
        duration: item.duration ? parseFloat(item.duration.N) : null,
        contactNames: item.contactNames?.L?.map(n => n.S) || null,
        contactCount: item.contactCount ? parseInt(item.contactCount.N) : null,
      },
    }));

    const nextCursor = result.LastEvaluatedKey
      ? Buffer.from(JSON.stringify(result.LastEvaluatedKey)).toString('base64')
      : null;

    return response(200, {
      items,
      nextCursor,
      totalCount: items.length,
    });
  } catch (error) {
    console.error('Error:', error);
    return response(500, { error: 'Internal server error' });
  }
};

function mapRetrievalStatus(item) {
  const status = item.retrievalStatus?.S || 'none';
  switch (status) {
    case 'in_progress': return { retrieving: true };
    case 'available': return {
      available: true,
      downloadURL: item.retrievalURL?.S,
      expiresAt: item.retrievalExpiresAt ? new Date(parseInt(item.retrievalExpiresAt.N)).toISOString() : null,
    };
    default: return { archived: true };
  }
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
