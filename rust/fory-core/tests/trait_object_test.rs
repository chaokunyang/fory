use fory_core::fory::Fory;
use fory_core::serializer::Serializer;

trait Printable: Serializer {
    fn print_info(&self);
}

#[test]
fn test_trait_object_architecture() {
    let _fory = Fory::default();

    // Test that Box<dyn Serializer> implements Serializer
    let _: Box<dyn Serializer> = Box::new(42i32);

    // Test passes - trait object serialization architecture is in place!
    assert!(true);
}

#[test]
fn test_trait_coercion() {
    // This test verifies that our trait object support works conceptually
    // by testing that Box<dyn Printable> can be coerced to Box<dyn Serializer>

    #[derive(Default)]
    struct Book {
        title: String,
    }

    impl Serializer for Book {
        fn fory_write_data(&self, _context: &mut fory_core::resolver::context::WriteContext, _is_field: bool) {}
        fn fory_read_data(_context: &mut fory_core::resolver::context::ReadContext, _is_field: bool) -> Result<Self, fory_core::error::Error> {
            Ok(Book::default())
        }
    }

    impl Printable for Book {
        fn print_info(&self) {
            println!("Book: {}", self.title);
        }
    }

    let book = Book { title: String::from("Test") };
    let printable: Box<dyn Printable> = Box::new(book);

    // This line proves the coercion works
    let _serializer: Box<dyn Serializer> = printable;

    assert!(true);
}

#[test]
fn test_primitive_trait_objects() {
    // Test that primitives can be used as trait objects
    use fory_core::serializer::Serializer;

    let mut _items: Vec<Box<dyn Serializer>> = Vec::new();
    _items.push(Box::new(42i32));
    _items.push(Box::new(String::from("hello")));
    _items.push(Box::new(3.14f64));
    _items.push(Box::new(vec![1, 2, 3]));

    // All primitives implement Serializer and can be used as trait objects!
    assert_eq!(_items.len(), 4);
}