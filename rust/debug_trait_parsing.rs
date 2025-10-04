use syn::{parse_str, Type, TypePath, PathArguments, GenericArgument};

fn main() {
    let type_str = "Rc<dyn Animal>";
    let ty: Type = parse_str(type_str).unwrap();

    if let Type::Path(TypePath { path, .. }) = &ty {
        if let Some(seg) = path.segments.last() {
            println!("Segment ident: {}", seg.ident);
            if seg.ident == "Rc" {
                if let PathArguments::AngleBracketed(args) = &seg.arguments {
                    if let Some(GenericArgument::Type(inner_ty)) = args.args.first() {
                        println!("Inner type: {:?}", inner_ty);

                        if let Type::TraitObject(trait_obj) = inner_ty {
                            println!("Trait object bounds: {:?}", trait_obj.bounds);

                            if let Some(bound) = trait_obj.bounds.first() {
                                if let syn::TypeParamBound::Trait(trait_bound) = bound {
                                    println!("Trait bound path: {:?}", trait_bound.path);
                                    if let Some(segment) = trait_bound.path.segments.last() {
                                        let trait_name = segment.ident.to_string();
                                        println!("Trait name: {}", trait_name);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}