use crate::error::AppError;
use uuid::Uuid;

/// Validation utilities for common data types
pub struct Validator;

impl Validator {
    /// Validate UUID format
    pub fn validate_uuid(id: &str) -> Result<Uuid, AppError> {
        Uuid::parse_str(id).map_err(|_| {
            AppError::InvalidInput(format!("Invalid UUID format: {}", id))
        })
    }

    /// Validate positive amount
    pub fn validate_positive_amount(amount: f64) -> Result<f64, AppError> {
        if amount <= 0.0 {
            return Err(AppError::InvalidInput(format!(
                "Amount must be positive, got: {}", 
                amount
            )));
        }
        Ok(amount)
    }

    /// Validate positive integer amount
    pub fn validate_positive_int_amount(amount: i32) -> Result<i32, AppError> {
        if amount <= 0 {
            return Err(AppError::InvalidInput(format!(
                "Amount must be positive, got: {}", 
                amount
            )));
        }
        Ok(amount)
    }

    /// Validate non-empty string
    pub fn validate_non_empty_string(value: &str, field_name: &str) -> Result<&str, AppError> {
        if value.trim().is_empty() {
            return Err(AppError::InvalidInput(format!(
                "{} cannot be empty", 
                field_name
            )));
        }
        Ok(value)
    }

    /// Validate that a collection has at least one item
    pub fn validate_non_empty_collection<T>(
        collection: &[T], 
        field_name: &str
    ) -> Result<&[T], AppError> {
        if collection.is_empty() {
            return Err(AppError::InvalidInput(format!(
                "{} cannot be empty", 
                field_name
            )));
        }
        Ok(collection)
    }

    /// Validate delivery address
    pub fn validate_delivery_address(address: &str) -> Result<&str, AppError> {
        Self::validate_non_empty_string(address, "delivery address")?;
        
        if address.len() < 5 {
            return Err(AppError::InvalidInput(
                "Delivery address must be at least 5 characters long".to_string()
            ));
        }
        
        Ok(address)
    }
}

/// Validation trait for proto message validation
pub trait ValidateProto {
    fn validate(&self) -> Result<(), AppError>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_uuid() {
        let valid_uuid = "550e8400-e29b-41d4-a716-446655440000";
        assert!(Validator::validate_uuid(valid_uuid).is_ok());

        let invalid_uuid = "not-a-uuid";
        assert!(Validator::validate_uuid(invalid_uuid).is_err());
    }

    #[test]
    fn test_validate_positive_amount() {
        assert!(Validator::validate_positive_amount(10.5).is_ok());
        assert!(Validator::validate_positive_amount(0.0).is_err());
        assert!(Validator::validate_positive_amount(-5.0).is_err());
    }

    #[test]
    fn test_validate_non_empty_string() {
        assert!(Validator::validate_non_empty_string("test", "field").is_ok());
        assert!(Validator::validate_non_empty_string("", "field").is_err());
        assert!(Validator::validate_non_empty_string("   ", "field").is_err());
    }

    #[test]
    fn test_validate_non_empty_collection() {
        let items = vec![1, 2, 3];
        assert!(Validator::validate_non_empty_collection(&items, "items").is_ok());

        let empty_items: Vec<i32> = vec![];
        assert!(Validator::validate_non_empty_collection(&empty_items, "items").is_err());
    }
}