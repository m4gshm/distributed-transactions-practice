use chrono::{DateTime, Utc};
use rdkafka::producer::{FutureProducer, FutureRecord};
use rdkafka::ClientConfig;
use serde_json::json;
use common::error::{AppError, AppResult};

/// Account event service for publishing balance events (mirrors Java AccountEventService)
pub struct AccountEventService {
    producer: FutureProducer,
    topic: String,
}

impl AccountEventService {
    pub fn new(broker_url: &str, topic: String) -> AppResult<Self> {
        let producer: FutureProducer = ClientConfig::new()
            .set("bootstrap.servers", broker_url)
            .set("message.timeout.ms", "5000")
            .create()
            .map_err(|e| AppError::ExternalService(format!("Failed to create Kafka producer: {}", e)))?;

        Ok(Self {
            producer,
            topic,
        })
    }

    /// Send account balance event to Kafka (mirrors Java sendAccountBalanceEvent)
    pub async fn send_account_balance_event(
        &self,
        client_id: &str,
        balance: f64,
        timestamp: DateTime<Utc>,
    ) -> AppResult<()> {
        let event = json!({
            "clientId": client_id,
            "balance": balance,
            "timestamp": timestamp.to_rfc3339(),
            "eventType": "ACCOUNT_BALANCE_CHANGED"
        });

        let key = client_id;
        let payload = event.to_string();

        let record = FutureRecord::to(&self.topic)
            .key(key)
            .payload(&payload);

        match self.producer.send(record, None).await {
            Ok(_) => {
                tracing::info!("Account balance event sent for client: {}", client_id);
                Ok(())
            }
            Err((e, _)) => {
                tracing::error!("Failed to send account balance event: {}", e);
                Err(AppError::ExternalService(format!("Kafka send error: {}", e)))
            }
        }
    }
}