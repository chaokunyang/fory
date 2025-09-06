use serde::{Deserialize, Serialize};
use fory_derive::Fory;
use std::collections::HashMap;
use crate::models::{TestDataGenerator, generate_random_string};

// Fury models
#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FurySimpleStruct {
    pub id: i32,
    pub name: String,
    pub active: bool,
    pub score: f64,
}

#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FurySimpleList {
    pub numbers: Vec<i32>,
    pub names: Vec<String>,
}

#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FurySimpleMap {
    pub string_to_int: HashMap<String, i32>,
    pub int_to_string: HashMap<i32, String>,
}

// Serde models
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdeSimpleStruct {
    pub id: i32,
    pub name: String,
    pub active: bool,
    pub score: f64,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdeSimpleList {
    pub numbers: Vec<i32>,
    pub names: Vec<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdeSimpleMap {
    pub string_to_int: HashMap<String, i32>,
    pub int_to_string: HashMap<i32, String>,
}

impl TestDataGenerator for FurySimpleStruct {
    type Data = FurySimpleStruct;
    
    fn generate_small() -> Self::Data {
        FurySimpleStruct {
            id: 1,
            name: "test".to_string(),
            active: true,
            score: 95.5,
        }
    }
    
    fn generate_medium() -> Self::Data {
        FurySimpleStruct {
            id: 12345,
            name: generate_random_string(50),
            active: true,
            score: 87.123456,
        }
    }
    
    fn generate_large() -> Self::Data {
        FurySimpleStruct {
            id: 999999,
            name: generate_random_string(200),
            active: false,
            score: 123.456789,
        }
    }
}

impl TestDataGenerator for FurySimpleList {
    type Data = FurySimpleList;
    
    fn generate_small() -> Self::Data {
        FurySimpleList {
            numbers: vec![1, 2, 3, 4, 5],
            names: vec!["a".to_string(), "b".to_string(), "c".to_string()],
        }
    }
    
    fn generate_medium() -> Self::Data {
        FurySimpleList {
            numbers: (1..=100).collect(),
            names: (1..=50).map(|i| format!("name_{}", i)).collect(),
        }
    }
    
    fn generate_large() -> Self::Data {
        FurySimpleList {
            numbers: (1..=1000).collect(),
            names: (1..=500).map(|_i| generate_random_string(20)).collect(),
        }
    }
}

impl TestDataGenerator for FurySimpleMap {
    type Data = FurySimpleMap;
    
    fn generate_small() -> Self::Data {
        let mut string_to_int = HashMap::new();
        string_to_int.insert("one".to_string(), 1);
        string_to_int.insert("two".to_string(), 2);
        
        let mut int_to_string = HashMap::new();
        int_to_string.insert(1, "one".to_string());
        int_to_string.insert(2, "two".to_string());
        
        FurySimpleMap {
            string_to_int,
            int_to_string,
        }
    }
    
    fn generate_medium() -> Self::Data {
        let mut string_to_int = HashMap::new();
        let mut int_to_string = HashMap::new();
        
        for i in 1..=50 {
            let key = format!("key_{}", i);
            string_to_int.insert(key.clone(), i);
            int_to_string.insert(i, key);
        }
        
        FurySimpleMap {
            string_to_int,
            int_to_string,
        }
    }
    
    fn generate_large() -> Self::Data {
        let mut string_to_int = HashMap::new();
        let mut int_to_string = HashMap::new();
        
        for i in 1..=500 {
            let key = generate_random_string(15);
            string_to_int.insert(key.clone(), i);
            int_to_string.insert(i, key);
        }
        
        FurySimpleMap {
            string_to_int,
            int_to_string,
        }
    }
}

// Conversion functions for Serde
impl From<FurySimpleStruct> for SerdeSimpleStruct {
    fn from(f: FurySimpleStruct) -> Self {
        SerdeSimpleStruct {
            id: f.id,
            name: f.name,
            active: f.active,
            score: f.score,
        }
    }
}

impl From<FurySimpleList> for SerdeSimpleList {
    fn from(f: FurySimpleList) -> Self {
        SerdeSimpleList {
            numbers: f.numbers,
            names: f.names,
        }
    }
}

impl From<FurySimpleMap> for SerdeSimpleMap {
    fn from(f: FurySimpleMap) -> Self {
        SerdeSimpleMap {
            string_to_int: f.string_to_int,
            int_to_string: f.int_to_string,
        }
    }
}

// Reverse conversions from Serde to Fury
impl From<SerdeSimpleStruct> for FurySimpleStruct {
    fn from(s: SerdeSimpleStruct) -> Self {
        FurySimpleStruct {
            id: s.id,
            name: s.name,
            active: s.active,
            score: s.score,
        }
    }
}

impl From<SerdeSimpleList> for FurySimpleList {
    fn from(s: SerdeSimpleList) -> Self {
        FurySimpleList {
            numbers: s.numbers,
            names: s.names,
        }
    }
}

impl From<SerdeSimpleMap> for FurySimpleMap {
    fn from(s: SerdeSimpleMap) -> Self {
        FurySimpleMap {
            string_to_int: s.string_to_int,
            int_to_string: s.int_to_string,
        }
    }
}
