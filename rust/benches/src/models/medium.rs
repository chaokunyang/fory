use serde::{Deserialize, Serialize};
use fory_derive::Fory;
use std::collections::HashMap;
use chrono::{DateTime, Utc, NaiveDateTime};
use crate::models::{TestDataGenerator, generate_random_string, generate_random_strings};

// Fury models
#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FuryAddress {
    pub street: String,
    pub city: String,
    pub country: String,
    pub zip_code: String,
}

#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FuryPerson {
    pub name: String,
    pub age: i32,
    pub address: FuryAddress,
    pub hobbies: Vec<String>,
    pub metadata: HashMap<String, String>,
    pub created_at: NaiveDateTime,
}

#[derive(Fory, Debug, Clone, PartialEq, Default)]
pub struct FuryCompany {
    pub name: String,
    pub employees: Vec<FuryPerson>,
    pub offices: HashMap<String, FuryAddress>,
    pub is_public: bool,
}

// Serde models
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdeAddress {
    pub street: String,
    pub city: String,
    pub country: String,
    pub zip_code: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdePerson {
    pub name: String,
    pub age: i32,
    pub address: SerdeAddress,
    pub hobbies: Vec<String>,
    pub metadata: HashMap<String, String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SerdeCompany {
    pub name: String,
    pub employees: Vec<SerdePerson>,
    pub offices: HashMap<String, SerdeAddress>,
    pub is_public: bool,
}

impl TestDataGenerator for FuryPerson {
    type Data = FuryPerson;
    
    fn generate_small() -> Self::Data {
        FuryPerson {
            name: "John Doe".to_string(),
            age: 30,
            address: FuryAddress {
                street: "123 Main St".to_string(),
                city: "New York".to_string(),
                country: "USA".to_string(),
                zip_code: "10001".to_string(),
            },
            hobbies: vec!["reading".to_string(), "coding".to_string()],
            metadata: HashMap::from([
                ("department".to_string(), "engineering".to_string()),
                ("level".to_string(), "senior".to_string()),
            ]),
            created_at: DateTime::from_timestamp(1640995200, 0).unwrap().naive_utc(),
        }
    }
    
    fn generate_medium() -> Self::Data {
        FuryPerson {
            name: generate_random_string(30),
            age: 35,
            address: FuryAddress {
                street: generate_random_string(50),
                city: generate_random_string(20),
                country: generate_random_string(15),
                zip_code: generate_random_string(10),
            },
            hobbies: generate_random_strings(10, 15),
            metadata: {
                let mut map = HashMap::new();
                for i in 1..=20 {
                    map.insert(format!("key_{}", i), generate_random_string(20));
                }
                map
            },
            created_at: DateTime::from_timestamp(1640995200, 0).unwrap().naive_utc(),
        }
    }
    
    fn generate_large() -> Self::Data {
        FuryPerson {
            name: generate_random_string(100),
            age: 40,
            address: FuryAddress {
                street: generate_random_string(100),
                city: generate_random_string(50),
                country: generate_random_string(30),
                zip_code: generate_random_string(20),
            },
            hobbies: generate_random_strings(50, 25),
            metadata: {
                let mut map = HashMap::new();
                for i in 1..=100 {
                    map.insert(format!("key_{}", i), generate_random_string(50));
                }
                map
            },
            created_at: DateTime::from_timestamp(1640995200, 0).unwrap().naive_utc(),
        }
    }
}

impl TestDataGenerator for FuryCompany {
    type Data = FuryCompany;
    
    fn generate_small() -> Self::Data {
        FuryCompany {
            name: "Tech Corp".to_string(),
            employees: vec![FuryPerson::generate_small()],
            offices: HashMap::from([
                ("HQ".to_string(), FuryAddress {
                    street: "456 Tech Ave".to_string(),
                    city: "San Francisco".to_string(),
                    country: "USA".to_string(),
                    zip_code: "94105".to_string(),
                }),
            ]),
            is_public: true,
        }
    }
    
    fn generate_medium() -> Self::Data {
        let mut employees = Vec::new();
        for i in 1..=10 {
            let mut person = FuryPerson::generate_medium();
            person.name = format!("Employee_{}", i);
            employees.push(person);
        }
        
        let mut offices = HashMap::new();
        for i in 1..=5 {
            offices.insert(
                format!("Office_{}", i),
                FuryAddress {
                    street: generate_random_string(50),
                    city: generate_random_string(20),
                    country: generate_random_string(15),
                    zip_code: generate_random_string(10),
                }
            );
        }
        
        FuryCompany {
            name: generate_random_string(50),
            employees,
            offices,
            is_public: true,
        }
    }
    
    fn generate_large() -> Self::Data {
        let mut employees = Vec::new();
        for i in 1..=100 {
            let mut person = FuryPerson::generate_large();
            person.name = format!("Employee_{}", i);
            employees.push(person);
        }
        
        let mut offices = HashMap::new();
        for i in 1..=20 {
            offices.insert(
                format!("Office_{}", i),
                FuryAddress {
                    street: generate_random_string(100),
                    city: generate_random_string(50),
                    country: generate_random_string(30),
                    zip_code: generate_random_string(20),
                }
            );
        }
        
        FuryCompany {
            name: generate_random_string(100),
            employees,
            offices,
            is_public: false,
        }
    }
}

// Conversion functions for Serde
impl From<FuryAddress> for SerdeAddress {
    fn from(f: FuryAddress) -> Self {
        SerdeAddress {
            street: f.street,
            city: f.city,
            country: f.country,
            zip_code: f.zip_code,
        }
    }
}

impl From<FuryPerson> for SerdePerson {
    fn from(f: FuryPerson) -> Self {
        SerdePerson {
            name: f.name,
            age: f.age,
            address: f.address.into(),
            hobbies: f.hobbies,
            metadata: f.metadata,
            created_at: f.created_at.and_utc(),
        }
    }
}

impl From<FuryCompany> for SerdeCompany {
    fn from(f: FuryCompany) -> Self {
        SerdeCompany {
            name: f.name,
            employees: f.employees.into_iter().map(|e| e.into()).collect(),
            offices: f.offices.into_iter().map(|(k, v)| (k, v.into())).collect(),
            is_public: f.is_public,
        }
    }
}

// Reverse conversions from Serde to Fury
impl From<SerdeAddress> for FuryAddress {
    fn from(s: SerdeAddress) -> Self {
        FuryAddress {
            street: s.street,
            city: s.city,
            country: s.country,
            zip_code: s.zip_code,
        }
    }
}

impl From<SerdePerson> for FuryPerson {
    fn from(s: SerdePerson) -> Self {
        FuryPerson {
            name: s.name,
            age: s.age,
            address: s.address.into(),
            hobbies: s.hobbies,
            metadata: s.metadata,
            created_at: s.created_at.naive_utc(),
        }
    }
}

impl From<SerdeCompany> for FuryCompany {
    fn from(s: SerdeCompany) -> Self {
        FuryCompany {
            name: s.name,
            employees: s.employees.into_iter().map(|e| e.into()).collect(),
            offices: s.offices.into_iter().map(|(k, v)| (k, v.into())).collect(),
            is_public: s.is_public,
        }
    }
}
