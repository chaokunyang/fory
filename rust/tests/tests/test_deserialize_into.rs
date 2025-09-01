use fory_core::fory::Fory;
use std::collections::HashMap;

#[test]
fn test_deserialize_into_basic_types() {
    let fory = Fory::default();
    
    // Test with String
    let original_string = "Hello, World!".to_string();
    let bin = fory.serialize(&original_string);
    let mut output_string = String::new();
    fory.deserialize_into(&bin, &mut output_string).expect("should work");
    assert_eq!(original_string, output_string);
    
    // Test with i32
    let original_i32 = 42i32;
    let bin = fory.serialize(&original_i32);
    let mut output_i32 = 0i32;
    fory.deserialize_into(&bin, &mut output_i32).expect("should work");
    assert_eq!(original_i32, output_i32);
    
    // Test with f64
    let original_f64 = 3.14159f64;
    let bin = fory.serialize(&original_f64);
    let mut output_f64 = 0.0f64;
    fory.deserialize_into(&bin, &mut output_f64).expect("should work");
    assert_eq!(original_f64, output_f64);
}

#[test]
fn test_deserialize_into_vec() {
    let fory = Fory::default();
    
    // Test with Vec
    let original_vec = vec![1, 2, 3, 4, 5];
    let bin = fory.serialize(&original_vec);
    let mut output_vec = Vec::new();
    fory.deserialize_into(&bin, &mut output_vec).expect("should work");
    assert_eq!(original_vec, output_vec);
}

#[test]
fn test_deserialize_into_hashmap() {
    let fory = Fory::default();
    
    // Test with HashMap
    let mut original_map = HashMap::new();
    original_map.insert("key1".to_string(), "value1".to_string());
    original_map.insert("key2".to_string(), "value2".to_string());
    let bin = fory.serialize(&original_map);
    let mut output_map = HashMap::new();
    fory.deserialize_into(&bin, &mut output_map).expect("should work");
    assert_eq!(original_map, output_map);
}

#[test]
fn test_deserialize_into_option() {
    let fory = Fory::default();
    
    // Test with Some
    let original_option = Some("test".to_string());
    let bin = fory.serialize(&original_option);
    let mut output_option = None;
    fory.deserialize_into(&bin, &mut output_option).expect("should work");
    assert_eq!(original_option, output_option);
    
    // Test with None
    let original_none: Option<String> = None;
    let bin = fory.serialize(&original_none);
    let mut output_none = Some("should be cleared".to_string());
    fory.deserialize_into(&bin, &mut output_none).expect("should work");
    assert_eq!(original_none, output_none);
}

#[test]
fn test_deserialize_into_reuse_allocation() {
    let fory = Fory::default();
    
    // Test that we can reuse the same allocation
    let original_vec = vec!["item1".to_string(), "item2".to_string(), "item3".to_string()];
    let bin = fory.serialize(&original_vec);
    
    let mut output_vec: Vec<String> = Vec::new();
    
    // First deserialization
    fory.deserialize_into(&bin, &mut output_vec).expect("should work");
    assert_eq!(original_vec, output_vec);
    
    // Second deserialization into the same vector (should clear and reuse)
    let original_vec2 = vec!["new1".to_string(), "new2".to_string()];
    let bin2 = fory.serialize(&original_vec2);
    fory.deserialize_into(&bin2, &mut output_vec).expect("should work");
    assert_eq!(original_vec2, output_vec);
    
    // Verify the vector was cleared and reused
    assert_eq!(output_vec.len(), 2);
    assert_eq!(output_vec[0], "new1");
    assert_eq!(output_vec[1], "new2");
}
