use std::env;
use std::path::PathBuf;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let proto_root = PathBuf::from(env::var("CARGO_MANIFEST_DIR")?)
        .parent()
        .unwrap()
        .parent()
        .unwrap()
        .join("proto");
    
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());

    // Configure tonic-build
    let mut config = tonic_build::configure()
        .build_server(true)
        .build_client(true)
        .out_dir(&out_dir);

    // Build orders service
    config
        .compile(
            &[proto_root.join("orders/v1/orders.proto")],
            &[&proto_root],
        )?;

    // Build payment service 
    config
        .compile(
            &[
                proto_root.join("payment/v1/payment.proto"),
                proto_root.join("payment/v1/account.proto"),
            ],
            &[&proto_root],
        )?;

    // Build reserve service
    config
        .compile(
            &[
                proto_root.join("reserve/v1/reserve.proto"),
                proto_root.join("reserve/v1/warehouse.proto"),
            ],
            &[&proto_root],
        )?;

    // Build TPC service
    config
        .compile(
            &[proto_root.join("tpc/v1/tpc.proto")],
            &[&proto_root],
        )?;

    Ok(())
}