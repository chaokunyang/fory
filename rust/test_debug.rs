// Minimal test to debug the macro issue
use fory_core::register_trait_type;
use fory_derive::fory_trait;
use fory_derive::Fory;

#[fory_trait]
trait SimpleTest {
    fn test(&self) -> String;
}

#[derive(Fory, Debug, Clone, PartialEq)]
struct SimpleImpl {
    value: String,
}

impl SimpleTest for SimpleImpl {
    fn test(&self) -> String {
        self.value.clone()
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

register_trait_type!(SimpleTest, SimpleImpl);

fn main() {
    println!("Test");
}