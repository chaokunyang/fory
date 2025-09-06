use serde_json;
use crate::serializers::Serializer;
use crate::models::simple::{SimpleStruct, SimpleList, SimpleMap, SerdeSimpleStruct, SerdeSimpleList, SerdeSimpleMap};
use crate::models::medium::{Person, Company, SerdePerson, SerdeCompany};
use crate::models::complex::{ECommerceData, SerdeECommerceData};
use crate::models::realworld::{SystemData, SerdeSystemData};

pub struct JsonSerializer;

impl JsonSerializer {
    pub fn new() -> Self {
        Self
    }
}

impl Serializer<SimpleStruct> for JsonSerializer {
    fn serialize(&self, data: &SimpleStruct) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleStruct = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleStruct, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleStruct = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<SimpleList> for JsonSerializer {
    fn serialize(&self, data: &SimpleList) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleList = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleList, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleList = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<SimpleMap> for JsonSerializer {
    fn serialize(&self, data: &SimpleMap) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleMap = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleMap, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleMap = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<Person> for JsonSerializer {
    fn serialize(&self, data: &Person) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdePerson = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<Person, Box<dyn std::error::Error>> {
        let serde_data: SerdePerson = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<Company> for JsonSerializer {
    fn serialize(&self, data: &Company) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeCompany = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<Company, Box<dyn std::error::Error>> {
        let serde_data: SerdeCompany = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<ECommerceData> for JsonSerializer {
    fn serialize(&self, data: &ECommerceData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeECommerceData = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<ECommerceData, Box<dyn std::error::Error>> {
        let serde_data: SerdeECommerceData = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<SystemData> for JsonSerializer {
    fn serialize(&self, data: &SystemData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSystemData = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SystemData, Box<dyn std::error::Error>> {
        let serde_data: SerdeSystemData = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

// Conversion functions from Serde to Fury models are defined in the model files
