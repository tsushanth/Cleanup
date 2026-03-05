const { DynamoDBClient, PutItemCommand, GetItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');

const dynamodb = new DynamoDBClient({ region: process.env.AWS_REGION });
const USERS_TABLE = process.env.USERS_TABLE;

function getUserId(event) {
  const cognitoId = event.requestContext?.identity?.cognitoIdentityId;
  if (cognitoId) return cognitoId;
  const headerIdentity = event.headers?.['x-identity-id'] || event.headers?.['X-Identity-Id'];
  if (headerIdentity) return headerIdentity;
  return null;
}

function mapProductToQuota(productId) {
  switch (productId) {
    case 'cleanup_archive_5gb__199': return 5000000000;       // 5 GB
    case 'cleanup_archive_25gb_499': return 25000000000;     // 25 GB
    case 'cleanup_archive_100gb_1499': return 100000000000;  // 100 GB
    default: return 0;
  }
}

exports.handler = async (event) => {
  try {
    const userId = getUserId(event);
    if (!userId) {
      return response(400, { error: 'Missing user identity' });
    }

    // Parse subscription info from body
    let subscriptionProductId = null;
    try {
      const bodyData = JSON.parse(event.body || '{}');
      subscriptionProductId = bodyData.subscriptionProductId || null;
    } catch (e) { /* ignore parse errors */ }

    const quotaBytes = mapProductToQuota(subscriptionProductId);

    // Check if user already exists
    const existing = await dynamodb.send(new GetItemCommand({
      TableName: USERS_TABLE,
      Key: { userId: { S: userId } },
    }));

    if (existing.Item) {
      // Update subscription and quota if provided
      if (subscriptionProductId && quotaBytes > 0) {
        await dynamodb.send(new UpdateItemCommand({
          TableName: USERS_TABLE,
          Key: { userId: { S: userId } },
          UpdateExpression: 'SET storageQuotaBytes = :quota, archiveSubscriptionProductId = :pid',
          ExpressionAttributeValues: {
            ':quota': { N: String(quotaBytes) },
            ':pid': { S: subscriptionProductId },
          },
        }));
      }
      return response(200, { message: 'User already registered', userId });
    }

    // Create new user record
    const item = {
      userId: { S: userId },
      createdAt: { N: String(Date.now()) },
      storageQuotaBytes: { N: String(quotaBytes) },
      storageUsedBytes: { N: '0' },
      selectedTier: { S: 'instant' },
    };
    if (subscriptionProductId) {
      item.archiveSubscriptionProductId = { S: subscriptionProductId };
    }

    await dynamodb.send(new PutItemCommand({
      TableName: USERS_TABLE,
      Item: item,
    }));

    return response(201, { message: 'User registered', userId });
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
