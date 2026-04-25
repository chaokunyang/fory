use fory_core::Fory;
use fory_derive::ForyObject;
use std::collections::HashMap;

#[derive(ForyObject, Debug, PartialEq, Eq, Clone)]
struct MyType {
    value: i32,
}

#[derive(ForyObject, Debug, PartialEq)]
struct ScalarEncodingStruct {
    a: u32,
    #[fory(encoding = "fixed")]
    b: u32,
    #[fory(encoding = "tagged")]
    c: u64,
    d: Option<u8>,
    e: MyType,
}

#[derive(ForyObject, Debug, PartialEq)]
struct ListEncodingStruct {
    values: Vec<i32>,
    nullable_values: Vec<Option<i32>>,
    maybe_values: Option<Vec<i32>>,
    #[fory(list(element(encoding = "fixed")))]
    fixed_values: Vec<i32>,
    #[fory(list(element(encoding = "fixed")))]
    fixed_nullable_values: Vec<Option<i32>>,
    maybe_fixed_nullable_values: Option<Vec<Option<i32>>>,
}

#[derive(ForyObject, Debug, PartialEq)]
struct MapEncodingStruct {
    data: HashMap<Option<i32>, Option<i32>>,
    #[fory(map(value(encoding = "fixed")))]
    data_fixed_value: HashMap<Option<i32>, Option<i32>>,
    #[fory(map(value(nullable = true, encoding = "fixed")))]
    nullable_data_fixed_value: HashMap<Option<i32>, Option<i32>>,
    #[fory(map(key(encoding = "fixed"), value(encoding = "fixed")))]
    data_fixed_key_value: HashMap<Option<i32>, Option<i32>>,
}

fn build_fory() -> Fory {
    let mut fory = Fory::default();
    fory.register::<MyType>(200).unwrap();
    fory.register::<ScalarEncodingStruct>(201).unwrap();
    fory.register::<ListEncodingStruct>(202).unwrap();
    fory.register::<MapEncodingStruct>(203).unwrap();
    fory
}

#[test]
fn test_scalar_field_codecs_roundtrip() {
    let fory = build_fory();
    let original = ScalarEncodingStruct {
        a: 17,
        b: 18,
        c: 19,
        d: Some(3),
        e: MyType { value: 7 },
    };
    let bytes = fory.serialize(&original).unwrap();
    let restored: ScalarEncodingStruct = fory.deserialize(&bytes).unwrap();
    assert_eq!(original, restored);
}

#[test]
fn test_list_nested_field_codecs_roundtrip() {
    let fory = build_fory();
    let original = ListEncodingStruct {
        values: vec![1, 2, 3],
        nullable_values: vec![Some(4), None, Some(5)],
        maybe_values: Some(vec![6, 7, 8]),
        fixed_values: vec![9, 10],
        fixed_nullable_values: vec![Some(11), None, Some(12)],
        maybe_fixed_nullable_values: Some(vec![Some(13), None, Some(14)]),
    };
    let bytes = fory.serialize(&original).unwrap();
    let restored: ListEncodingStruct = fory.deserialize(&bytes).unwrap();
    assert_eq!(original, restored);
}

#[test]
fn test_map_nested_field_codecs_roundtrip() {
    let fory = build_fory();
    let mut data = HashMap::new();
    data.insert(Some(1), Some(2));
    data.insert(None, Some(3));

    let mut data_fixed_value = HashMap::new();
    data_fixed_value.insert(Some(4), Some(5));
    data_fixed_value.insert(None, None);

    let mut nullable_data_fixed_value = HashMap::new();
    nullable_data_fixed_value.insert(Some(6), Some(7));
    nullable_data_fixed_value.insert(None, None);

    let mut data_fixed_key_value = HashMap::new();
    data_fixed_key_value.insert(Some(8), Some(9));
    data_fixed_key_value.insert(None, Some(10));

    let original = MapEncodingStruct {
        data,
        data_fixed_value,
        nullable_data_fixed_value,
        data_fixed_key_value,
    };
    let bytes = fory.serialize(&original).unwrap();
    let restored: MapEncodingStruct = fory.deserialize(&bytes).unwrap();
    assert_eq!(original, restored);
}
