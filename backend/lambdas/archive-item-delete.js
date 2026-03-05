const { S3Client, DeleteObjectCommand } = require('@aws-sdk/client-s3');
const { DynamoDBClient, DeleteItemCommand, GetItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');

const s3 = new S3Client({ region: process.env.AWS_REGION });
const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });

const BUCKET = process.env.ARCHIVE_BUCKET;
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
    const itemId = event.pathParameters.itemId;

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
    const fileSize = parseInt(itemResult.Item.fileSize.N);

    // Delete from S3
    await s3.send(new DeleteObjectCommand({
      Bucket: BUCKET,
      Key: s3Key,
    }));

    // Delete from DynamoDB
    await dynamodb.send(new DeleteItemCommand({
      TableName: ITEMS_TABLE,
      Key: {
        userId: { S: userId },
        itemId: { S: itemId },
      },
    }));

    // Update user storage usage
    await dynamodb.send(new UpdateItemCommand({
      TableName: USERS_TABLE,
      Key: { userId: { S: userId } },
      UpdateExpression: 'ADD storageUsedBytes :size',
      ExpressionAttributeValues: {
        ':size': { N: String(-fileSize) },
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
