// Re-export all generated protobuf code
pub mod orders {
    pub mod v1 {
        tonic::include_proto!("orders.v1");
    }
}

pub mod payment {
    pub mod v1 {
        tonic::include_proto!("payment.v1");
    }
}

pub mod reserve {
    pub mod v1 {
        tonic::include_proto!("reserve.v1");
    }
}

pub mod account {
    pub mod v1 {
        tonic::include_proto!("payment.v1");
    }
}

pub mod warehouse {
    pub mod v1 {
        tonic::include_proto!("warehouse.v1");
    }
}

pub mod tpc {
    pub mod v1 {
        tonic::include_proto!("tpc.v1");
    }
}