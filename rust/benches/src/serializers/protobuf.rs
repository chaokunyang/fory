use prost::Message;
use crate::serializers::{Serializer, naive_datetime_to_timestamp, timestamp_to_naive_datetime};
use crate::models::simple::{FurySimpleStruct, FurySimpleList, FurySimpleMap};
use crate::models::medium::{FuryPerson, FuryCompany, FuryAddress};
use crate::models::complex::{FuryECommerceData, FuryProduct, FuryOrderItem, FuryCustomer, FuryOrder};
use crate::models::realworld::{FurySystemData, FuryLogEntry, FuryUserProfile, FuryAPIMetrics};

// Import protobuf types from the generated code
use crate::{SimpleStruct, SimpleList, SimpleMap, Address, Person, Company, Product, OrderItem, Customer, Order, ECommerceData, LogEntry, UserProfile, ApiMetrics, SystemData};

pub struct ProtobufSerializer;

impl ProtobufSerializer {
    pub fn new() -> Self {
        Self
    }
}

// Conversion functions from Fury models to Protobuf models
impl From<&FurySimpleStruct> for SimpleStruct {
    fn from(f: &FurySimpleStruct) -> Self {
        SimpleStruct {
            id: f.id,
            name: f.name.clone(),
            active: f.active,
            score: f.score,
        }
    }
}

impl From<&FurySimpleList> for SimpleList {
    fn from(f: &FurySimpleList) -> Self {
        SimpleList {
            numbers: f.numbers.clone(),
            names: f.names.clone(),
        }
    }
}

impl From<&FurySimpleMap> for SimpleMap {
    fn from(f: &FurySimpleMap) -> Self {
        SimpleMap {
            string_to_int: f.string_to_int.clone(),
            int_to_string: f.int_to_string.clone(),
        }
    }
}

impl From<&FuryAddress> for Address {
    fn from(f: &FuryAddress) -> Self {
        Address {
            street: f.street.clone(),
            city: f.city.clone(),
            country: f.country.clone(),
            zip_code: f.zip_code.clone(),
        }
    }
}

impl From<&FuryPerson> for Person {
    fn from(f: &FuryPerson) -> Self {
        Person {
            name: f.name.clone(),
            age: f.age,
            address: Some((&f.address).into()),
            hobbies: f.hobbies.clone(),
            metadata: f.metadata.clone(),
            created_at: Some(naive_datetime_to_timestamp(f.created_at)),
        }
    }
}

impl From<&FuryCompany> for Company {
    fn from(f: &FuryCompany) -> Self {
        Company {
            name: f.name.clone(),
            employees: f.employees.iter().map(|e| e.into()).collect(),
            offices: f.offices.iter().map(|(k, v)| (k.clone(), v.into())).collect(),
            is_public: f.is_public,
        }
    }
}

impl From<&FuryProduct> for Product {
    fn from(f: &FuryProduct) -> Self {
        Product {
            id: f.id.clone(),
            name: f.name.clone(),
            price: f.price,
            categories: f.categories.clone(),
            attributes: f.attributes.clone(),
        }
    }
}

impl From<&FuryOrderItem> for OrderItem {
    fn from(f: &FuryOrderItem) -> Self {
        OrderItem {
            product: Some((&f.product).into()),
            quantity: f.quantity,
            unit_price: f.unit_price,
            customizations: f.customizations.clone(),
        }
    }
}

impl From<&FuryCustomer> for Customer {
    fn from(f: &FuryCustomer) -> Self {
        Customer {
            id: f.id.clone(),
            name: f.name.clone(),
            email: f.email.clone(),
            phone_numbers: f.phone_numbers.clone(),
            preferences: f.preferences.clone(),
            member_since: Some(naive_datetime_to_timestamp(f.member_since)),
        }
    }
}

impl From<&FuryOrder> for Order {
    fn from(f: &FuryOrder) -> Self {
        Order {
            id: f.id.clone(),
            customer: Some((&f.customer).into()),
            items: f.items.iter().map(|i| i.into()).collect(),
            total_amount: f.total_amount,
            status: f.status.clone(),
            order_date: Some(naive_datetime_to_timestamp(f.order_date)),
            metadata: f.metadata.clone(),
        }
    }
}

impl From<&FuryECommerceData> for ECommerceData {
    fn from(f: &FuryECommerceData) -> Self {
        ECommerceData {
            orders: f.orders.iter().map(|o| o.into()).collect(),
            customers: f.customers.iter().map(|c| c.into()).collect(),
            products: f.products.iter().map(|p| p.into()).collect(),
            order_lookup: f.order_lookup.iter().map(|(k, v)| (k.clone(), v.into())).collect(),
        }
    }
}

impl From<&FuryLogEntry> for LogEntry {
    fn from(f: &FuryLogEntry) -> Self {
        LogEntry {
            id: f.id.clone(),
            level: f.level,
            message: f.message.clone(),
            service: f.service.clone(),
            timestamp: Some(naive_datetime_to_timestamp(f.timestamp)),
            context: f.context.clone(),
            tags: f.tags.clone(),
            duration_ms: f.duration_ms,
        }
    }
}

impl From<&FuryUserProfile> for UserProfile {
    fn from(f: &FuryUserProfile) -> Self {
        UserProfile {
            user_id: f.user_id.clone(),
            username: f.username.clone(),
            email: f.email.clone(),
            preferences: f.preferences.clone(),
            permissions: f.permissions.clone(),
            last_login: Some(naive_datetime_to_timestamp(f.last_login)),
            is_active: f.is_active,
        }
    }
}

impl From<&FuryAPIMetrics> for ApiMetrics {
    fn from(f: &FuryAPIMetrics) -> Self {
        ApiMetrics {
            endpoint: f.endpoint.clone(),
            request_count: f.request_count,
            avg_response_time: f.avg_response_time,
            error_count: f.error_count,
            status_codes: f.status_codes.clone(),
            measured_at: Some(naive_datetime_to_timestamp(f.measured_at)),
        }
    }
}

impl From<&FurySystemData> for SystemData {
    fn from(f: &FurySystemData) -> Self {
        SystemData {
            logs: f.logs.iter().map(|l| l.into()).collect(),
            users: f.users.iter().map(|u| u.into()).collect(),
            metrics: f.metrics.iter().map(|m| m.into()).collect(),
            system_info: f.system_info.clone(),
        }
    }
}

// Conversion functions from Protobuf models to Fury models
impl From<SimpleStruct> for FurySimpleStruct {
    fn from(p: SimpleStruct) -> Self {
        FurySimpleStruct {
            id: p.id,
            name: p.name,
            active: p.active,
            score: p.score,
        }
    }
}

impl From<SimpleList> for FurySimpleList {
    fn from(p: SimpleList) -> Self {
        FurySimpleList {
            numbers: p.numbers,
            names: p.names,
        }
    }
}

impl From<SimpleMap> for FurySimpleMap {
    fn from(p: SimpleMap) -> Self {
        FurySimpleMap {
            string_to_int: p.string_to_int,
            int_to_string: p.int_to_string,
        }
    }
}

impl From<Address> for FuryAddress {
    fn from(p: Address) -> Self {
        FuryAddress {
            street: p.street,
            city: p.city,
            country: p.country,
            zip_code: p.zip_code,
        }
    }
}

impl From<Person> for FuryPerson {
    fn from(p: Person) -> Self {
        FuryPerson {
            name: p.name,
            age: p.age,
            address: p.address.map(|a| a.into()).unwrap_or_default(),
            hobbies: p.hobbies,
            metadata: p.metadata,
            created_at: p.created_at.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
        }
    }
}

impl From<Company> for FuryCompany {
    fn from(p: Company) -> Self {
        FuryCompany {
            name: p.name,
            employees: p.employees.into_iter().map(|e| e.into()).collect(),
            offices: p.offices.into_iter().map(|(k, v)| (k, v.into())).collect(),
            is_public: p.is_public,
        }
    }
}

impl From<Product> for FuryProduct {
    fn from(p: Product) -> Self {
        FuryProduct {
            id: p.id,
            name: p.name,
            price: p.price,
            categories: p.categories,
            attributes: p.attributes,
        }
    }
}

impl From<OrderItem> for FuryOrderItem {
    fn from(p: OrderItem) -> Self {
        FuryOrderItem {
            product: p.product.map(|prod| prod.into()).unwrap_or_default(),
            quantity: p.quantity,
            unit_price: p.unit_price,
            customizations: p.customizations,
        }
    }
}

impl From<Customer> for FuryCustomer {
    fn from(p: Customer) -> Self {
        FuryCustomer {
            id: p.id,
            name: p.name,
            email: p.email,
            phone_numbers: p.phone_numbers,
            preferences: p.preferences,
            member_since: p.member_since.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
        }
    }
}

impl From<Order> for FuryOrder {
    fn from(p: Order) -> Self {
        FuryOrder {
            id: p.id,
            customer: p.customer.map(|c| c.into()).unwrap_or_default(),
            items: p.items.into_iter().map(|i| i.into()).collect(),
            total_amount: p.total_amount,
            status: p.status,
            order_date: p.order_date.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
            metadata: p.metadata,
        }
    }
}

impl From<ECommerceData> for FuryECommerceData {
    fn from(p: ECommerceData) -> Self {
        FuryECommerceData {
            orders: p.orders.into_iter().map(|o| o.into()).collect(),
            customers: p.customers.into_iter().map(|c| c.into()).collect(),
            products: p.products.into_iter().map(|prod| prod.into()).collect(),
            order_lookup: p.order_lookup.into_iter().map(|(k, v)| (k, v.into())).collect(),
        }
    }
}

impl From<LogEntry> for FuryLogEntry {
    fn from(p: LogEntry) -> Self {
        FuryLogEntry {
            id: p.id,
            level: p.level,
            message: p.message,
            service: p.service,
            timestamp: p.timestamp.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
            context: p.context,
            tags: p.tags,
            duration_ms: p.duration_ms,
        }
    }
}

impl From<UserProfile> for FuryUserProfile {
    fn from(p: UserProfile) -> Self {
        FuryUserProfile {
            user_id: p.user_id,
            username: p.username,
            email: p.email,
            preferences: p.preferences,
            permissions: p.permissions,
            last_login: p.last_login.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
            is_active: p.is_active,
        }
    }
}

impl From<ApiMetrics> for FuryAPIMetrics {
    fn from(p: ApiMetrics) -> Self {
        FuryAPIMetrics {
            endpoint: p.endpoint,
            request_count: p.request_count,
            avg_response_time: p.avg_response_time,
            error_count: p.error_count,
            status_codes: p.status_codes,
            measured_at: p.measured_at.map(|ts| timestamp_to_naive_datetime(ts)).unwrap_or_default(),
        }
    }
}

impl From<SystemData> for FurySystemData {
    fn from(p: SystemData) -> Self {
        FurySystemData {
            logs: p.logs.into_iter().map(|l| l.into()).collect(),
            users: p.users.into_iter().map(|u| u.into()).collect(),
            metrics: p.metrics.into_iter().map(|m| m.into()).collect(),
            system_info: p.system_info,
        }
    }
}

// Serializer implementations
impl Serializer<FurySimpleStruct> for ProtobufSerializer {
    fn serialize(&self, data: &FurySimpleStruct) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: SimpleStruct = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleStruct, Box<dyn std::error::Error>> {
        let proto = SimpleStruct::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FurySimpleList> for ProtobufSerializer {
    fn serialize(&self, data: &FurySimpleList) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: SimpleList = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleList, Box<dyn std::error::Error>> {
        let proto = SimpleList::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FurySimpleMap> for ProtobufSerializer {
    fn serialize(&self, data: &FurySimpleMap) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: SimpleMap = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySimpleMap, Box<dyn std::error::Error>> {
        let proto = SimpleMap::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FuryPerson> for ProtobufSerializer {
    fn serialize(&self, data: &FuryPerson) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: Person = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryPerson, Box<dyn std::error::Error>> {
        let proto = Person::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FuryCompany> for ProtobufSerializer {
    fn serialize(&self, data: &FuryCompany) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: Company = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryCompany, Box<dyn std::error::Error>> {
        let proto = Company::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FuryECommerceData> for ProtobufSerializer {
    fn serialize(&self, data: &FuryECommerceData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: ECommerceData = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FuryECommerceData, Box<dyn std::error::Error>> {
        let proto = ECommerceData::decode(data)?;
        Ok(proto.into())
    }
}

impl Serializer<FurySystemData> for ProtobufSerializer {
    fn serialize(&self, data: &FurySystemData) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
        let proto: SystemData = data.into();
        let mut buf = Vec::new();
        proto.encode(&mut buf)?;
        Ok(buf)
    }
    
    fn deserialize(&self, data: &[u8]) -> Result<FurySystemData, Box<dyn std::error::Error>> {
        let proto = SystemData::decode(data)?;
        Ok(proto.into())
    }
}
