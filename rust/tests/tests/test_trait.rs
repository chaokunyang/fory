// use fory_core::fory::Fory;
// use fory_core::types::Mode::Compatible;
// use fory_derive::Fory;
// // Define a trait with a method
// trait Printable {
//     fn print_info(&self);
// }

// // Implement the trait for a struct
// #[derive(Fory, Debug, Default)]
// struct Book {
//     title: String,
//     author: String,
// }

// impl Printable for Book {
//     fn print_info(&self) {
//         println!("Book: \"{}\" by {}", self.title, self.author);
//     }
// }

// // Implement the trait for another struct
// #[derive(Fory, Debug, Default)]
// struct Movie {
//     title: String,
//     director: String,
// }

// impl Printable for Movie {
//     fn print_info(&self) {
//         println!("Movie: \"{}\" directed by {}", self.title, self.director);
//     }
// }

// #[test]
// fn test_trait_serialization() {
//     let fory = Fory::default();
//     // Create instances of different types
//     let book = Book {
//         title: String::from("The Lord of the Rings"),
//         author: String::from("J.R.R. Tolkien"),
//     };

//     let movie = Movie {
//         title: String::from("Interstellar"),
//         director: String::from("Christopher Nolan"),
//     };

//     // Create a vector of trait objects (Box<dyn Printable>)
//     // This allows storing different types that implement Printable
//     let items: Vec<Box<dyn Printable>> = vec![Box::new(book), Box::new(movie)];

//     let serialized = fory.serialize(&items);
//     let deserialized: Vec<Box<dyn Printable>> = fory.deserialize(&serialized).unwrap();
// }
