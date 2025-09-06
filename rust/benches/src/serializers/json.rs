use serde_json;
use crate::serializers::Serializer;
use crate::models::simple::{FurySimpleStruct, FurySimpleList, FurySimpleMap, SerdeSimpleStruct, SerdeSimpleList, SerdeSimpleMap};
use crate::models::medium::{FuryPerson, FuryCompany, SerdePerson, SerdeCompany};
use crate::models::complex::{FuryECommerceData, SerdeECommerceData};
use crate::models::realworld::{FurySystemData, SerdeSystemData};

pub struct JsonSerializer;

impl JsonSerializer {
    pub fn new() -> Self {
        Self
    }
}

impl Serializer<FurySimpleStruct> for JsonSerializer {
    fn serialize(&self, data: &FurySimpleStruct) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleStruct = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleStruct, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleStruct = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FurySimpleList> for JsonSerializer {
    fn serialize(&self, data: &FurySimpleList) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleList = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleList, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleList = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FurySimpleMap> for JsonSerializer {
    fn serialize(&self, data: &FurySimpleMap) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleMap = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleMap, Box<dyn std::error::Error>> {
        let serde_data: SerdeSimpleMap = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FuryPerson> for JsonSerializer {
    fn serialize(&self, data: &FuryPerson) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdePerson = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryPerson, Box<dyn std::error::Error>> {
        let serde_data: SerdePerson = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FuryCompany> for JsonSerializer {
    fn serialize(&self, data: &FuryCompany) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeCompany = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryCompany, Box<dyn std::error::Error>> {
        let serde_data: SerdeCompany = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FuryECommerceData> for JsonSerializer {
    fn serialize(&self, data: &FuryECommerceData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeECommerceData = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryECommerceData, Box<dyn std::error::Error>> {
        let serde_data: SerdeECommerceData = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

impl Serializer<FurySystemData> for JsonSerializer {
    fn serialize(&self, data: &FurySystemData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let serde_data: SerdeSystemData = data.clone().into();
        Ok(serde_json::to_vec(&serde_data)?)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySystemData, Box<dyn std::error::Error>> {
        let serde_data: SerdeSystemData = serde_json::from_slice(data)?;
        Ok(serde_data.into())
    }
}

// Conversion functions from Serde to Fury models are defined in the model files
