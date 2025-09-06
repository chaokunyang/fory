use std::path::Path;

fn main() {
    println!("cargo:warning=Build script running");
    println!("cargo:warning=OUT_DIR: {}", std::env::var("OUT_DIR").unwrap());
    
    let proto_files = [
        "proto/simple.proto",
        "proto/medium.proto", 
        "proto/complex.proto",
        "proto/realworld.proto",
    ];

    for proto_file in &proto_files {
        if Path::new(proto_file).exists() {
            println!("cargo:rerun-if-changed={}", proto_file);
            println!("cargo:warning=Found proto file: {}", proto_file);
        } else {
            println!("cargo:warning=Proto file not found: {}", proto_file);
        }
    }

    let mut config = prost_build::Config::new();
    // Don't set out_dir, use the default OUT_DIR
    
    println!("cargo:warning=About to compile protobuf files");
    config
        .compile_protos(&proto_files, &["proto/"])
        .expect("Failed to compile protobuf files");
    println!("cargo:warning=Protobuf compilation completed");
}
