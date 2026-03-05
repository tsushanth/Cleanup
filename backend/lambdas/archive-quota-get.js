const { DynamoDBClient, GetItemCommand } = require('@aws-sdk/client-dynamodb');

const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });
const USERS_TABLE = process.env.USERS_TABLE;
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

    const result = await dynamodb.send(new GetItemCommand({
      TableName: USERS_TABLE,
      Key: { userId: { S: userId } },
    }));

    if (!result.Item) {
      return response(404, { error: 'User not found' });
    }

    const user = result.Item;
    const totalBytes = parseInt(user.storageQuotaBytes?.N || '0');
    const usedBytes = parseInt(user.storageUsedBytes?.N || '0');

    // Count items
    const { DynamoDBClient: DDBClient, QueryCommand } = require('@aws-sdk/client-dynamodb');
    const countResult = await dynamodb.send(new QueryCommand({
      TableName: ITEMS_TABLE,
      KeyConditionExpression: 'userId = :uid',
      ExpressionAttributeValues: { ':uid': { S: userId } },
      Select: 'COUNT',
    }));

    return response(200, {
      totalBytes,
      usedBytes,
      itemCount: countResult.Count || 0,
      subscriptionProductId: user.archiveSubscriptionProductId?.S || null,
      tier: mapProductToTier(user.archiveSubscriptionProductId?.S),
    });
  } catch (error) {
    console.error('Error:', error);
    return response(500, { error: 'Internal server error' });
  }
};

function mapProductToTier(productId) {
  // Return the product ID as-is — iOS ArchiveSubscriptionTier uses product IDs as raw values
  const validProducts = ['cleanup_archive_5gb__199', 'cleanup_archive_25gb_499', 'cleanup_archive_100gb_1499'];
  return validProducts.includes(productId) ? productId : null;
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
