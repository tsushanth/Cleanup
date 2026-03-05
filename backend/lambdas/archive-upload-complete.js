const { DynamoDBClient, PutItemCommand, UpdateItemCommand, GetItemCommand } = require('@aws-sdk/client-dynamodb');

const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });
const ITEMS_TABLE = process.env.ITEMS_TABLE;
const USERS_TABLE = process.env.USERS_TABLE;

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
    const { itemId, fileName, fileType, fileSize, storageTier, thumbnailBase64, creationDate, pixelWidth, pixelHeight, duration, contactNames, contactCount } = body;

    if (!itemId || !fileName || !fileType || !fileSize || !storageTier) {
      return response(400, { error: 'Missing required fields' });
    }

    // Check quota before accepting upload
    const userResult = await dynamodb.send(new GetItemCommand({
      TableName: USERS_TABLE,
      Key: { userId: { S: userId } },
    }));

    if (userResult.Item) {
      const quotaBytes = parseInt(userResult.Item.storageQuotaBytes?.N || '0');
      const usedBytes = parseInt(userResult.Item.storageUsedBytes?.N || '0');
      if (quotaBytes > 0 && (usedBytes + fileSize) > quotaBytes) {
        return response(413, {
          error: 'Storage quota exceeded',
          usedBytes,
          quotaBytes,
          requiredBytes: fileSize,
        });
      }
    }

    // Write item to DynamoDB
    const item = {
      userId: { S: userId },
      itemId: { S: itemId },
      originalFilename: { S: fileName },
      fileType: { S: fileType },
      fileSize: { N: String(fileSize) },
      storageTier: { S: storageTier },
      s3Key: { S: `users/${userId}/items/${itemId}/${fileName}` },
      uploadedAt: { N: String(Date.now()) },
      retrievalStatus: { S: 'none' },
    };

    if (thumbnailBase64) item.thumbnailData = { B: Buffer.from(thumbnailBase64, 'base64') };
    if (creationDate) item.creationDate = { S: creationDate };
    if (pixelWidth) item.pixelWidth = { N: String(pixelWidth) };
    if (pixelHeight) item.pixelHeight = { N: String(pixelHeight) };
    if (duration) item.duration = { N: String(duration) };
    if (contactNames) item.contactNames = { L: contactNames.map(n => ({ S: n })) };
    if (contactCount) item.contactCount = { N: String(contactCount) };

    await dynamodb.send(new PutItemCommand({
      TableName: ITEMS_TABLE,
      Item: item,
    }));

    // Update user storage usage
    await dynamodb.send(new UpdateItemCommand({
      TableName: USERS_TABLE,
      Key: { userId: { S: userId } },
      UpdateExpression: 'ADD storageUsedBytes :size',
      ExpressionAttributeValues: {
        ':size': { N: String(fileSize) },
      },
    }));

    return response(200, { success: true });
  } catch (error) {
    console.error('Error:', error);
    return response(500, { error: 'Internal server error' });
  }
};

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
