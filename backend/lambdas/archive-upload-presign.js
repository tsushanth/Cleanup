const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
const { DynamoDBClient, GetItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');

const s3 = new S3Client({ region: process.env.AWS_REGION });
const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });

const BUCKET = process.env.ARCHIVE_BUCKET;
const USERS_TABLE = process.env.USERS_TABLE;

const TIER_TO_STORAGE_CLASS = {
  instant: 'GLACIER_IR',
  flexible: 'GLACIER',
  deep: 'DEEP_ARCHIVE',
};

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
    const { itemId, filename, fileSize, mimeType, storageTier } = body;

    if (!itemId || !filename || !fileSize || !storageTier) {
      return response(400, { error: 'Missing required fields' });
    }

    // Check user quota
    const user = await getUser(userId);
    if (!user) {
      return response(404, { error: 'User not found' });
    }

    const quotaBytes = parseInt(user.storageQuotaBytes?.N || '0');
    const usedBytes = parseInt(user.storageUsedBytes?.N || '0');

    if (usedBytes + fileSize > quotaBytes) {
      return response(413, { error: 'Storage quota exceeded' });
    }

    // Generate presigned URL
    const storageClass = TIER_TO_STORAGE_CLASS[storageTier] || 'GLACIER_IR';
    const s3Key = `users/${userId}/items/${itemId}/${filename}`;

    const command = new PutObjectCommand({
      Bucket: BUCKET,
      Key: s3Key,
      ContentType: mimeType || 'application/octet-stream',
      StorageClass: storageClass,
    });

    const uploadURL = await getSignedUrl(s3, command, { expiresIn: 3600 });

    return response(200, {
      uploadURL,
      s3Key,
      expiresAt: new Date(Date.now() + 3600000).toISOString(),
    });
  } catch (error) {
    console.error('Error:', error);
    return response(500, { error: 'Internal server error' });
  }
};

async function getUser(userId) {
  const result = await dynamodb.send(new GetItemCommand({
    TableName: USERS_TABLE,
    Key: { userId: { S: userId } },
  }));
  return result.Item;
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
