use fory_core::fory::Fory;
use crate::serializers::Serializer;
use crate::models::simple::{FurySimpleStruct, FurySimpleList, FurySimpleMap};
use crate::models::medium::{FuryAddress, FuryPerson, FuryCompany};
use crate::models::complex::{FuryProduct, FuryOrderItem, FuryCustomer, FuryOrder, FuryECommerceData};
use crate::models::realworld::{FuryLogEntry, FuryUserProfile, FuryAPIMetrics, FurySystemData};

pub struct FurySerializer {
    fory: Fory,
}

impl FurySerializer {
    pub fn new() -> Self {
        let mut fory = Fory::default();
        
        // Register simple types
        fory.register::<FurySimpleStruct>(100);
        fory.register::<FurySimpleList>(101);
        fory.register::<FurySimpleMap>(102);
        
        // Register medium types
        fory.register::<FuryAddress>(200);
        fory.register::<FuryPerson>(201);
        fory.register::<FuryCompany>(202);
        
        // Register complex types
        fory.register::<FuryProduct>(300);
        fory.register::<FuryOrderItem>(301);
        fory.register::<FuryCustomer>(302);
        fory.register::<FuryOrder>(303);
        fory.register::<FuryECommerceData>(304);
        
        // Register realworld types
        fory.register::<FuryLogEntry>(400);
        fory.register::<FuryUserProfile>(401);
        fory.register::<FuryAPIMetrics>(402);
        fory.register::<FurySystemData>(403);
        
        Self { fory }
    }
}

impl Serializer<FurySimpleStruct> for FurySerializer {
    fn serialize(&self, data: &FurySimpleStruct) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleStruct, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FurySimpleList> for FurySerializer {
    fn serialize(&self, data: &FurySimpleList) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleList, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FurySimpleMap> for FurySerializer {
    fn serialize(&self, data: &FurySimpleMap) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleMap, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FuryPerson> for FurySerializer {
    fn serialize(&self, data: &FuryPerson) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryPerson, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FuryCompany> for FurySerializer {
    fn serialize(&self, data: &FuryCompany) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryCompany, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FuryECommerceData> for FurySerializer {
    fn serialize(&self, data: &FuryECommerceData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryECommerceData, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<FurySystemData> for FurySerializer {
    fn serialize(&self, data: &FurySystemData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySystemData, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}
