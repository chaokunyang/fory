use fory_derive::Fory;

trait SimpleT {
    fn name(&self) -> &str;
}

#[derive(Fory)]
struct Simple {
    field: Box<dyn SimpleT>,
}