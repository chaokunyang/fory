use fory_core::fory::Fory;
use crate::serializers::Serializer;
use crate::models::simple::{SimpleStruct, SimpleList, SimpleMap};
use crate::models::medium::{FuryAddress, Person, Company};
use crate::models::complex::{FuryProduct, FuryOrderItem, FuryCustomer, FuryOrder, ECommerceData};
use crate::models::realworld::{FuryLogEntry, FuryUserProfile, FuryAPIMetrics, SystemData};

pub struct FurySerializer {
    fory: Fory,
}

impl FurySerializer {
    pub fn new() -> Self {
        let mut fory = Fory::default();
        
        // Register simple types
        fory.register::<SimpleStruct>(100);
        fory.register::<SimpleList>(101);
        fory.register::<SimpleMap>(102);
        
        // Register medium types
        fory.register::<FuryAddress>(200);
        fory.register::<Person>(201);
        fory.register::<Company>(202);
        
        // Register complex types
        fory.register::<FuryProduct>(300);
        fory.register::<FuryOrderItem>(301);
        fory.register::<FuryCustomer>(302);
        fory.register::<FuryOrder>(303);
        fory.register::<ECommerceData>(304);
        
        // Register realworld types
        fory.register::<FuryLogEntry>(400);
        fory.register::<FuryUserProfile>(401);
        fory.register::<FuryAPIMetrics>(402);
        fory.register::<SystemData>(403);
        
        Self { fory }
    }
}

impl Serializer<SimpleStruct> for FurySerializer {
    fn serialize(&self, data: &SimpleStruct) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleStruct, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<SimpleList> for FurySerializer {
    fn serialize(&self, data: &SimpleList) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleList, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<SimpleMap> for FurySerializer {
    fn serialize(&self, data: &SimpleMap) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SimpleMap, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<Person> for FurySerializer {
    fn serialize(&self, data: &Person) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<Person, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<Company> for FurySerializer {
    fn serialize(&self, data: &Company) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<Company, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<ECommerceData> for FurySerializer {
    fn serialize(&self, data: &ECommerceData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<ECommerceData, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}

impl Serializer<SystemData> for FurySerializer {
    fn serialize(&self, data: &SystemData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        Ok(self.fory.serialize(data))
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<SystemData, Box<dyn std::error::Error>> {
        Ok(self.fory.deserialize(data)?)
    }
}
