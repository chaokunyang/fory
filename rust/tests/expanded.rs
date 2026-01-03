#![feature(prelude_import)]
#[macro_use]
extern crate std;
#[prelude_import]
use std::prelude::rust_2021::*;
use fory_core::fory::Fory;
use fory_derive::ForyObject;
use std::collections::HashMap;
extern crate test;
#[rustc_test_marker = "test_simple"]
#[doc(hidden)]
pub const test_simple: test::TestDescAndFn = test::TestDescAndFn {
    desc: test::TestDesc {
        name: test::StaticTestName("test_simple"),
        ignore: false,
        ignore_message: ::core::option::Option::None,
        source_file: "tests/tests/test_one_struct.rs",
        start_line: 23usize,
        start_col: 4usize,
        end_line: 23usize,
        end_col: 15usize,
        compile_fail: false,
        no_run: false,
        should_panic: test::ShouldPanic::No,
        test_type: test::TestType::IntegrationTest,
    },
    testfn: test::StaticTestFn(
        #[coverage(off)]
        || test::assert_test_result(test_simple()),
    ),
};
fn test_simple() {
    #[fory_debug]
    struct Animal1 {
        f1: HashMap<i8, Vec<i8>>,
        f2: String,
        f3: Vec<i8>,
        f5: String,
        f6: Vec<i8>,
        f7: i8,
        last: i8,
    }
    use fory_core::ForyDefault as _;
    impl fory_core::ForyDefault for Animal1 {
        fn fory_default() -> Self {
            Self {
                f7: <i8 as fory_core::ForyDefault>::fory_default(),
                last: <i8 as fory_core::ForyDefault>::fory_default(),
                f2: <String as fory_core::ForyDefault>::fory_default(),
                f5: <String as fory_core::ForyDefault>::fory_default(),
                f3: <Vec<i8> as fory_core::ForyDefault>::fory_default(),
                f6: <Vec<i8> as fory_core::ForyDefault>::fory_default(),
                f1: <HashMap<i8, Vec<i8>> as fory_core::ForyDefault>::fory_default(),
            }
        }
    }
    impl std::default::Default for Animal1 {
        fn default() -> Self {
            Self::fory_default()
        }
    }
    impl fory_core::StructSerializer for Animal1 {
        #[inline(always)]
        fn fory_type_index() -> u32 {
            0u32
        }
        #[inline(always)]
        fn fory_actual_type_id(
            type_id: u32,
            register_by_name: bool,
            compatible: bool,
        ) -> u32 {
            fory_core::serializer::struct_::actual_type_id(
                type_id,
                register_by_name,
                compatible,
            )
        }
        fn fory_get_sorted_field_names() -> &'static [&'static str] {
            &["f7", "last", "f2", "f5", "f3", "f6", "f1"]
        }
        fn fory_fields_info(
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<Vec<fory_core::meta::FieldInfo>, fory_core::error::Error> {
            let field_infos: Vec<fory_core::meta::FieldInfo> = <[_]>::into_vec(
                ::alloc::boxed::box_new([
                    fory_core::meta::FieldInfo::new(
                        "f7",
                        fory_core::meta::FieldType::new(
                            <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            false,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "last",
                        fory_core::meta::FieldType::new(
                            <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            false,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f2",
                        fory_core::meta::FieldType::new(
                            <String as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f5",
                        fory_core::meta::FieldType::new(
                            <String as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f3",
                        fory_core::meta::FieldType::new(
                            31u32,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f6",
                        fory_core::meta::FieldType::new(
                            31u32,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f1",
                        fory_core::meta::FieldType::new(
                            <HashMap<
                                i8,
                                Vec<i8>,
                            > as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            true,
                            <[_]>::into_vec(
                                ::alloc::boxed::box_new([
                                    fory_core::meta::FieldType::new(
                                        <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                            type_resolver,
                                        )?,
                                        false,
                                        ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                                    ),
                                    fory_core::meta::FieldType::new(
                                        31u32,
                                        true,
                                        ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                                    ),
                                ]),
                            ) as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                ]),
            );
            Ok(field_infos)
        }
        #[inline]
        fn fory_read_compatible(
            context: &mut fory_core::resolver::context::ReadContext,
            type_info: std::rc::Rc<fory_core::TypeInfo>,
        ) -> Result<Self, fory_core::error::Error> {
            let fields = type_info.get_type_meta().get_field_infos().clone();
            let mut _f7: i8 = 0 as i8;
            let mut _last: i8 = 0 as i8;
            let mut _f2: Option<String> = None;
            let mut _f5: Option<String> = None;
            let mut _f3: Option<Vec<i8>> = None;
            let mut _f6: Option<Vec<i8>> = None;
            let mut _f1: Option<HashMap<i8, Vec<i8>>> = None;
            let meta = context
                .get_type_info(&std::any::TypeId::of::<Self>())?
                .get_type_meta();
            let local_type_hash = meta.get_hash();
            let remote_type_hash = type_info.get_type_meta().get_hash();
            if remote_type_hash == local_type_hash {
                <Self as fory_core::Serializer>::fory_read_data(context)
            } else {
                for _field in fields.iter() {
                    match _field.field_id {
                        0i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f7",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f7 = <i8 as fory_core::Serializer>::fory_read(
                                    context,
                                    true,
                                    false,
                                )?;
                            } else {
                                _f7 = <i8 as fory_core::Serializer>::fory_read_data(
                                    context,
                                )?;
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f7",
                                (&_f7) as &dyn std::any::Any,
                                context,
                            );
                        }
                        1i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "last",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _last = <i8 as fory_core::Serializer>::fory_read(
                                    context,
                                    true,
                                    false,
                                )?;
                            } else {
                                _last = <i8 as fory_core::Serializer>::fory_read_data(
                                    context,
                                )?;
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "last",
                                (&_last) as &dyn std::any::Any,
                                context,
                            );
                        }
                        2i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f2",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f2 = Some(
                                    <String as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f2 = Some(
                                    <String as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f2",
                                (&_f2) as &dyn std::any::Any,
                                context,
                            );
                        }
                        3i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f5",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f5 = Some(
                                    <String as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f5 = Some(
                                    <String as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f5",
                                (&_f5) as &dyn std::any::Any,
                                context,
                            );
                        }
                        4i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f3",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f3 = Some(
                                    <Vec<
                                        i8,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f3 = Some(
                                    <Vec<i8> as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f3",
                                (&_f3) as &dyn std::any::Any,
                                context,
                            );
                        }
                        5i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f6",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f6 = Some(
                                    <Vec<
                                        i8,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f6 = Some(
                                    <Vec<i8> as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f6",
                                (&_f6) as &dyn std::any::Any,
                                context,
                            );
                        }
                        6i16 => {
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                "f1",
                                context,
                            );
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f1 = Some(
                                    <HashMap<
                                        i8,
                                        Vec<i8>,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f1 = Some(
                                    <HashMap<
                                        i8,
                                        Vec<i8>,
                                    > as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                "f1",
                                (&_f1) as &dyn std::any::Any,
                                context,
                            );
                        }
                        _ => {
                            let field_type = &_field.field_type;
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                field_type.type_id,
                                field_type.nullable,
                            );
                            let field_name = _field.field_name.as_str();
                            fory_core::serializer::struct_::struct_before_read_field(
                                "Animal1",
                                field_name,
                                context,
                            );
                            fory_core::serializer::skip::skip_field_value(
                                context,
                                &field_type,
                                read_ref_flag,
                            )?;
                            let placeholder: &dyn std::any::Any = &();
                            fory_core::serializer::struct_::struct_after_read_field(
                                "Animal1",
                                field_name,
                                placeholder,
                                context,
                            );
                        }
                    }
                }
                Ok(Self {
                    f7: _f7,
                    last: _last,
                    f2: _f2.unwrap_or_default(),
                    f5: _f5.unwrap_or_default(),
                    f3: _f3.unwrap_or_default(),
                    f6: _f6.unwrap_or_default(),
                    f1: _f1.unwrap_or_default(),
                })
            }
        }
    }
    impl fory_core::Serializer for Animal1 {
        #[inline(always)]
        fn fory_get_type_id(
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<u32, fory_core::error::Error> {
            type_resolver.get_type_id(&std::any::TypeId::of::<Self>(), 0u32)
        }
        #[inline(always)]
        fn fory_type_id_dyn(
            &self,
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<u32, fory_core::error::Error> {
            Self::fory_get_type_id(type_resolver)
        }
        #[inline(always)]
        fn as_any(&self) -> &dyn std::any::Any {
            self
        }
        #[inline(always)]
        fn FORY_STATIC_TYPE_ID -> fory_core::TypeId
        where
            Self: Sized,
        {
            fory_core::TypeId::STRUCT
        }
        #[inline(always)]
        fn fory_reserved_space() -> usize {
            <i8 as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <i8 as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <String as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <String as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <Vec<i8> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <Vec<i8> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <HashMap<i8, Vec<i8>> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
        }
        #[inline(always)]
        fn fory_write(
            &self,
            context: &mut fory_core::resolver::context::WriteContext,
            write_ref_info: bool,
            write_type_info: bool,
            _: bool,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::write::<
                Self,
            >(self, context, write_ref_info, write_type_info)
        }
        #[inline]
        fn fory_write_data(
            &self,
            context: &mut fory_core::resolver::context::WriteContext,
        ) -> Result<(), fory_core::error::Error> {
            if context.is_check_struct_version() {
                context.writer.write_i32(-362304397i32);
            }
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f7",
                (&self.f7) as &dyn std::any::Any,
                context,
            );
            <i8 as fory_core::Serializer>::fory_write_data(&self.f7, context)?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f7",
                (&self.f7) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "last",
                (&self.last) as &dyn std::any::Any,
                context,
            );
            <i8 as fory_core::Serializer>::fory_write_data(&self.last, context)?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "last",
                (&self.last) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f2",
                (&self.f2) as &dyn std::any::Any,
                context,
            );
            <String as fory_core::Serializer>::fory_write(
                &self.f2,
                context,
                true,
                false,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f2",
                (&self.f2) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f5",
                (&self.f5) as &dyn std::any::Any,
                context,
            );
            <String as fory_core::Serializer>::fory_write(
                &self.f5,
                context,
                true,
                false,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f5",
                (&self.f5) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f3",
                (&self.f3) as &dyn std::any::Any,
                context,
            );
            <Vec<
                i8,
            > as fory_core::Serializer>::fory_write(
                &self.f3,
                context,
                true,
                false,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f3",
                (&self.f3) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f6",
                (&self.f6) as &dyn std::any::Any,
                context,
            );
            <Vec<
                i8,
            > as fory_core::Serializer>::fory_write(
                &self.f6,
                context,
                true,
                false,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f6",
                (&self.f6) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_write_field(
                "Animal1",
                "f1",
                (&self.f1) as &dyn std::any::Any,
                context,
            );
            <HashMap<
                i8,
                Vec<i8>,
            > as fory_core::Serializer>::fory_write(
                &self.f1,
                context,
                true,
                false,
                true,
            )?;
            fory_core::serializer::struct_::struct_after_write_field(
                "Animal1",
                "f1",
                (&self.f1) as &dyn std::any::Any,
                context,
            );
            Ok(())
        }
        #[inline(always)]
        fn fory_write_type_info(
            context: &mut fory_core::resolver::context::WriteContext,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::write_type_info::<Self>(context)
        }
        #[inline(always)]
        fn fory_read(
            context: &mut fory_core::resolver::context::ReadContext,
            read_ref_info: bool,
            read_type_info: bool,
        ) -> Result<Self, fory_core::error::Error> {
            let ref_flag = if read_ref_info {
                context.reader.read_i8()?
            } else {
                fory_core::RefFlag::NotNullValue as i8
            };
            if ref_flag == (fory_core::RefFlag::NotNullValue as i8)
                || ref_flag == (fory_core::RefFlag::RefValue as i8)
            {
                if context.is_compatible() {
                    let type_info = if read_type_info {
                        context.read_any_typeinfo()?
                    } else {
                        let rs_type_id = std::any::TypeId::of::<Self>();
                        context.get_type_info(&rs_type_id)?
                    };
                    <Animal1 as fory_core::StructSerializer>::fory_read_compatible(
                        context,
                        type_info,
                    )
                } else {
                    if read_type_info {
                        <Self as fory_core::Serializer>::fory_read_type_info(context)?;
                    }
                    <Self as fory_core::Serializer>::fory_read_data(context)
                }
            } else if ref_flag == (fory_core::RefFlag::Null as i8) {
                Ok(<Self as fory_core::ForyDefault>::fory_default())
            } else {
                Err(
                    fory_core::error::Error::invalid_ref(
                        ::alloc::__export::must_use({
                            ::alloc::fmt::format(
                                format_args!("Unknown ref flag, value:{0}", ref_flag),
                            )
                        }),
                    ),
                )
            }
        }
        #[inline(always)]
        fn fory_read_with_type_info(
            context: &mut fory_core::resolver::context::ReadContext,
            read_ref_info: bool,
            type_info: std::rc::Rc<fory_core::TypeInfo>,
        ) -> Result<Self, fory_core::error::Error> {
            let ref_flag = if read_ref_info {
                context.reader.read_i8()?
            } else {
                fory_core::RefFlag::NotNullValue as i8
            };
            if ref_flag == (fory_core::RefFlag::NotNullValue as i8)
                || ref_flag == (fory_core::RefFlag::RefValue as i8)
            {
                if context.is_compatible() {
                    <Animal1 as fory_core::StructSerializer>::fory_read_compatible(
                        context,
                        type_info,
                    )
                } else {
                    <Self as fory_core::Serializer>::fory_read_data(context)
                }
            } else if ref_flag == (fory_core::RefFlag::Null as i8) {
                Ok(<Self as fory_core::ForyDefault>::fory_default())
            } else {
                Err(
                    fory_core::error::Error::invalid_ref(
                        ::alloc::__export::must_use({
                            ::alloc::fmt::format(
                                format_args!("Unknown ref flag, value:{0}", ref_flag),
                            )
                        }),
                    ),
                )
            }
        }
        #[inline]
        fn fory_read_data(
            context: &mut fory_core::resolver::context::ReadContext,
        ) -> Result<Self, fory_core::error::Error> {
            if context.is_check_struct_version() {
                let read_version = context.reader.read_i32()?;
                let type_name = std::any::type_name::<Self>();
                fory_core::meta::TypeMeta::check_struct_version(
                    read_version,
                    -362304397i32,
                    type_name,
                )?;
            }
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f7",
                context,
            );
            let _f7 = <i8 as fory_core::Serializer>::fory_read_data(context)?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f7",
                (&_f7) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "last",
                context,
            );
            let _last = <i8 as fory_core::Serializer>::fory_read_data(context)?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "last",
                (&_last) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f2",
                context,
            );
            let _f2 = <String as fory_core::Serializer>::fory_read(
                context,
                true,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f2",
                (&_f2) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f5",
                context,
            );
            let _f5 = <String as fory_core::Serializer>::fory_read(
                context,
                true,
                false,
            )?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f5",
                (&_f5) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f3",
                context,
            );
            let _f3 = <Vec<
                i8,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f3",
                (&_f3) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f6",
                context,
            );
            let _f6 = <Vec<
                i8,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f6",
                (&_f6) as &dyn std::any::Any,
                context,
            );
            fory_core::serializer::struct_::struct_before_read_field(
                "Animal1",
                "f1",
                context,
            );
            let _f1 = <HashMap<
                i8,
                Vec<i8>,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            fory_core::serializer::struct_::struct_after_read_field(
                "Animal1",
                "f1",
                (&_f1) as &dyn std::any::Any,
                context,
            );
            Ok(Self {
                f7: _f7,
                last: _last,
                f2: _f2,
                f5: _f5,
                f3: _f3,
                f6: _f6,
                f1: _f1,
            })
        }
        #[inline(always)]
        fn fory_read_type_info(
            context: &mut fory_core::resolver::context::ReadContext,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::read_type_info::<Self>(context)
        }
    }
    #[automatically_derived]
    impl ::core::fmt::Debug for Animal1 {
        #[inline]
        fn fmt(&self, f: &mut ::core::fmt::Formatter) -> ::core::fmt::Result {
            let names: &'static _ = &["f1", "f2", "f3", "f5", "f6", "f7", "last"];
            let values: &[&dyn ::core::fmt::Debug] = &[
                &self.f1,
                &self.f2,
                &self.f3,
                &self.f5,
                &self.f6,
                &self.f7,
                &&self.last,
            ];
            ::core::fmt::Formatter::debug_struct_fields_finish(
                f,
                "Animal1",
                names,
                values,
            )
        }
    }
    struct Animal2 {
        f1: HashMap<i8, Vec<i8>>,
        f3: Vec<i8>,
        f4: String,
        f5: i8,
        f6: Vec<i16>,
        f7: i16,
        last: i8,
    }
    use fory_core::ForyDefault as _;
    impl fory_core::ForyDefault for Animal2 {
        fn fory_default() -> Self {
            Self {
                f7: <i16 as fory_core::ForyDefault>::fory_default(),
                f5: <i8 as fory_core::ForyDefault>::fory_default(),
                last: <i8 as fory_core::ForyDefault>::fory_default(),
                f4: <String as fory_core::ForyDefault>::fory_default(),
                f3: <Vec<i8> as fory_core::ForyDefault>::fory_default(),
                f6: <Vec<i16> as fory_core::ForyDefault>::fory_default(),
                f1: <HashMap<i8, Vec<i8>> as fory_core::ForyDefault>::fory_default(),
            }
        }
    }
    impl std::default::Default for Animal2 {
        fn default() -> Self {
            Self::fory_default()
        }
    }
    impl fory_core::StructSerializer for Animal2 {
        #[inline(always)]
        fn fory_type_index() -> u32 {
            1u32
        }
        #[inline(always)]
        fn fory_actual_type_id(
            type_id: u32,
            register_by_name: bool,
            compatible: bool,
        ) -> u32 {
            fory_core::serializer::struct_::actual_type_id(
                type_id,
                register_by_name,
                compatible,
            )
        }
        fn fory_get_sorted_field_names() -> &'static [&'static str] {
            &["f7", "f5", "last", "f4", "f3", "f6", "f1"]
        }
        fn fory_fields_info(
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<Vec<fory_core::meta::FieldInfo>, fory_core::error::Error> {
            let field_infos: Vec<fory_core::meta::FieldInfo> = <[_]>::into_vec(
                ::alloc::boxed::box_new([
                    fory_core::meta::FieldInfo::new(
                        "f7",
                        fory_core::meta::FieldType::new(
                            <i16 as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            false,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f5",
                        fory_core::meta::FieldType::new(
                            <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            false,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "last",
                        fory_core::meta::FieldType::new(
                            <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            false,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f4",
                        fory_core::meta::FieldType::new(
                            <String as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f3",
                        fory_core::meta::FieldType::new(
                            31u32,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f6",
                        fory_core::meta::FieldType::new(
                            32u32,
                            true,
                            ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                    fory_core::meta::FieldInfo::new(
                        "f1",
                        fory_core::meta::FieldType::new(
                            <HashMap<
                                i8,
                                Vec<i8>,
                            > as fory_core::serializer::Serializer>::fory_get_type_id(
                                type_resolver,
                            )?,
                            true,
                            <[_]>::into_vec(
                                ::alloc::boxed::box_new([
                                    fory_core::meta::FieldType::new(
                                        <i8 as fory_core::serializer::Serializer>::fory_get_type_id(
                                            type_resolver,
                                        )?,
                                        false,
                                        ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                                    ),
                                    fory_core::meta::FieldType::new(
                                        31u32,
                                        true,
                                        ::alloc::vec::Vec::new() as Vec<fory_core::meta::FieldType>,
                                    ),
                                ]),
                            ) as Vec<fory_core::meta::FieldType>,
                        ),
                    ),
                ]),
            );
            Ok(field_infos)
        }
        #[inline]
        fn fory_read_compatible(
            context: &mut fory_core::resolver::context::ReadContext,
            type_info: std::rc::Rc<fory_core::TypeInfo>,
        ) -> Result<Self, fory_core::error::Error> {
            let fields = type_info.get_type_meta().get_field_infos().clone();
            let mut _f7: i16 = 0 as i16;
            let mut _f5: i8 = 0 as i8;
            let mut _last: i8 = 0 as i8;
            let mut _f4: Option<String> = None;
            let mut _f3: Option<Vec<i8>> = None;
            let mut _f6: Option<Vec<i16>> = None;
            let mut _f1: Option<HashMap<i8, Vec<i8>>> = None;
            let meta = context
                .get_type_info(&std::any::TypeId::of::<Self>())?
                .get_type_meta();
            let local_type_hash = meta.get_hash();
            let remote_type_hash = type_info.get_type_meta().get_hash();
            if remote_type_hash == local_type_hash {
                <Self as fory_core::Serializer>::fory_read_data(context)
            } else {
                for _field in fields.iter() {
                    match _field.field_id {
                        0i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f7 = <i16 as fory_core::Serializer>::fory_read(
                                    context,
                                    true,
                                    false,
                                )?;
                            } else {
                                _f7 = <i16 as fory_core::Serializer>::fory_read_data(
                                    context,
                                )?;
                            }
                        }
                        1i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f5 = <i8 as fory_core::Serializer>::fory_read(
                                    context,
                                    true,
                                    false,
                                )?;
                            } else {
                                _f5 = <i8 as fory_core::Serializer>::fory_read_data(
                                    context,
                                )?;
                            }
                        }
                        2i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _last = <i8 as fory_core::Serializer>::fory_read(
                                    context,
                                    true,
                                    false,
                                )?;
                            } else {
                                _last = <i8 as fory_core::Serializer>::fory_read_data(
                                    context,
                                )?;
                            }
                        }
                        3i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f4 = Some(
                                    <String as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f4 = Some(
                                    <String as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                        }
                        4i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f3 = Some(
                                    <Vec<
                                        i8,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f3 = Some(
                                    <Vec<i8> as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                        }
                        5i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f6 = Some(
                                    <Vec<
                                        i16,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f6 = Some(
                                    <Vec<
                                        i16,
                                    > as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                        }
                        6i16 => {
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                _field.field_type.type_id,
                                _field.field_type.nullable,
                            );
                            if read_ref_flag {
                                _f1 = Some(
                                    <HashMap<
                                        i8,
                                        Vec<i8>,
                                    > as fory_core::Serializer>::fory_read(
                                        context,
                                        true,
                                        false,
                                    )?,
                                );
                            } else {
                                _f1 = Some(
                                    <HashMap<
                                        i8,
                                        Vec<i8>,
                                    > as fory_core::Serializer>::fory_read_data(context)?,
                                );
                            }
                        }
                        _ => {
                            let field_type = &_field.field_type;
                            let read_ref_flag = fory_core::serializer::util::field_requires_ref_flag(
                                field_type.type_id,
                                field_type.nullable,
                            );
                            fory_core::serializer::skip::skip_field_value(
                                context,
                                field_type,
                                read_ref_flag,
                            )?;
                        }
                    }
                }
                Ok(Self {
                    f7: _f7,
                    f5: _f5,
                    last: _last,
                    f4: _f4.unwrap_or_default(),
                    f3: _f3.unwrap_or_default(),
                    f6: _f6.unwrap_or_default(),
                    f1: _f1.unwrap_or_default(),
                })
            }
        }
    }
    impl fory_core::Serializer for Animal2 {
        #[inline(always)]
        fn fory_get_type_id(
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<u32, fory_core::error::Error> {
            type_resolver.get_type_id(&std::any::TypeId::of::<Self>(), 1u32)
        }
        #[inline(always)]
        fn fory_type_id_dyn(
            &self,
            type_resolver: &fory_core::resolver::type_resolver::TypeResolver,
        ) -> Result<u32, fory_core::error::Error> {
            Self::fory_get_type_id(type_resolver)
        }
        #[inline(always)]
        fn as_any(&self) -> &dyn std::any::Any {
            self
        }
        #[inline(always)]
        fn FORY_STATIC_TYPE_ID -> fory_core::TypeId
        where
            Self: Sized,
        {
            fory_core::TypeId::STRUCT
        }
        #[inline(always)]
        fn fory_reserved_space() -> usize {
            <i16 as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <i8 as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <i8 as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <String as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <Vec<i8> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <Vec<i16> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
                + <HashMap<i8, Vec<i8>> as fory_core::Serializer>::fory_reserved_space()
                + fory_core::types::SIZE_OF_REF_AND_TYPE
        }
        #[inline(always)]
        fn fory_write(
            &self,
            context: &mut fory_core::resolver::context::WriteContext,
            write_ref_info: bool,
            write_type_info: bool,
            _: bool,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::write::<
                Self,
            >(self, context, write_ref_info, write_type_info)
        }
        #[inline]
        fn fory_write_data(
            &self,
            context: &mut fory_core::resolver::context::WriteContext,
        ) -> Result<(), fory_core::error::Error> {
            if context.is_check_struct_version() {
                context.writer.write_i32(-1615591914i32);
            }
            <i16 as fory_core::Serializer>::fory_write_data(&self.f7, context)?;
            <i8 as fory_core::Serializer>::fory_write_data(&self.f5, context)?;
            <i8 as fory_core::Serializer>::fory_write_data(&self.last, context)?;
            <String as fory_core::Serializer>::fory_write(
                &self.f4,
                context,
                true,
                false,
                false,
            )?;
            <Vec<
                i8,
            > as fory_core::Serializer>::fory_write(
                &self.f3,
                context,
                true,
                false,
                false,
            )?;
            <Vec<
                i16,
            > as fory_core::Serializer>::fory_write(
                &self.f6,
                context,
                true,
                false,
                false,
            )?;
            <HashMap<
                i8,
                Vec<i8>,
            > as fory_core::Serializer>::fory_write(
                &self.f1,
                context,
                true,
                false,
                true,
            )?;
            Ok(())
        }
        #[inline(always)]
        fn fory_write_type_info(
            context: &mut fory_core::resolver::context::WriteContext,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::write_type_info::<Self>(context)
        }
        #[inline(always)]
        fn fory_read(
            context: &mut fory_core::resolver::context::ReadContext,
            read_ref_info: bool,
            read_type_info: bool,
        ) -> Result<Self, fory_core::error::Error> {
            let ref_flag = if read_ref_info {
                context.reader.read_i8()?
            } else {
                fory_core::RefFlag::NotNullValue as i8
            };
            if ref_flag == (fory_core::RefFlag::NotNullValue as i8)
                || ref_flag == (fory_core::RefFlag::RefValue as i8)
            {
                if context.is_compatible() {
                    let type_info = if read_type_info {
                        context.read_any_typeinfo()?
                    } else {
                        let rs_type_id = std::any::TypeId::of::<Self>();
                        context.get_type_info(&rs_type_id)?
                    };
                    <Animal2 as fory_core::StructSerializer>::fory_read_compatible(
                        context,
                        type_info,
                    )
                } else {
                    if read_type_info {
                        <Self as fory_core::Serializer>::fory_read_type_info(context)?;
                    }
                    <Self as fory_core::Serializer>::fory_read_data(context)
                }
            } else if ref_flag == (fory_core::RefFlag::Null as i8) {
                Ok(<Self as fory_core::ForyDefault>::fory_default())
            } else {
                Err(
                    fory_core::error::Error::invalid_ref(
                        ::alloc::__export::must_use({
                            ::alloc::fmt::format(
                                format_args!("Unknown ref flag, value:{0}", ref_flag),
                            )
                        }),
                    ),
                )
            }
        }
        #[inline(always)]
        fn fory_read_with_type_info(
            context: &mut fory_core::resolver::context::ReadContext,
            read_ref_info: bool,
            type_info: std::rc::Rc<fory_core::TypeInfo>,
        ) -> Result<Self, fory_core::error::Error> {
            let ref_flag = if read_ref_info {
                context.reader.read_i8()?
            } else {
                fory_core::RefFlag::NotNullValue as i8
            };
            if ref_flag == (fory_core::RefFlag::NotNullValue as i8)
                || ref_flag == (fory_core::RefFlag::RefValue as i8)
            {
                if context.is_compatible() {
                    <Animal2 as fory_core::StructSerializer>::fory_read_compatible(
                        context,
                        type_info,
                    )
                } else {
                    <Self as fory_core::Serializer>::fory_read_data(context)
                }
            } else if ref_flag == (fory_core::RefFlag::Null as i8) {
                Ok(<Self as fory_core::ForyDefault>::fory_default())
            } else {
                Err(
                    fory_core::error::Error::invalid_ref(
                        ::alloc::__export::must_use({
                            ::alloc::fmt::format(
                                format_args!("Unknown ref flag, value:{0}", ref_flag),
                            )
                        }),
                    ),
                )
            }
        }
        #[inline]
        fn fory_read_data(
            context: &mut fory_core::resolver::context::ReadContext,
        ) -> Result<Self, fory_core::error::Error> {
            if context.is_check_struct_version() {
                let read_version = context.reader.read_i32()?;
                let type_name = std::any::type_name::<Self>();
                fory_core::meta::TypeMeta::check_struct_version(
                    read_version,
                    -1615591914i32,
                    type_name,
                )?;
            }
            let _f7 = <i16 as fory_core::Serializer>::fory_read_data(context)?;
            let _f5 = <i8 as fory_core::Serializer>::fory_read_data(context)?;
            let _last = <i8 as fory_core::Serializer>::fory_read_data(context)?;
            let _f4 = <String as fory_core::Serializer>::fory_read(
                context,
                true,
                false,
            )?;
            let _f3 = <Vec<
                i8,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            let _f6 = <Vec<
                i16,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            let _f1 = <HashMap<
                i8,
                Vec<i8>,
            > as fory_core::Serializer>::fory_read(context, true, false)?;
            Ok(Self {
                f7: _f7,
                f5: _f5,
                last: _last,
                f4: _f4,
                f3: _f3,
                f6: _f6,
                f1: _f1,
            })
        }
        #[inline(always)]
        fn fory_read_type_info(
            context: &mut fory_core::resolver::context::ReadContext,
        ) -> Result<(), fory_core::error::Error> {
            fory_core::serializer::struct_::read_type_info::<Self>(context)
        }
    }
    #[automatically_derived]
    impl ::core::fmt::Debug for Animal2 {
        #[inline]
        fn fmt(&self, f: &mut ::core::fmt::Formatter) -> ::core::fmt::Result {
            let names: &'static _ = &["f1", "f3", "f4", "f5", "f6", "f7", "last"];
            let values: &[&dyn ::core::fmt::Debug] = &[
                &self.f1,
                &self.f3,
                &self.f4,
                &self.f5,
                &self.f6,
                &self.f7,
                &&self.last,
            ];
            ::core::fmt::Formatter::debug_struct_fields_finish(
                f,
                "Animal2",
                names,
                values,
            )
        }
    }
    let mut fory1 = Fory::default().compatible(true);
    let mut fory2 = Fory::default().compatible(true);
    fory1.register::<Animal1>(999).unwrap();
    fory2.register::<Animal2>(999).unwrap();
    let animal: Animal1 = Animal1 {
        f1: HashMap::from([(1, <[_]>::into_vec(::alloc::boxed::box_new([2])))]),
        f2: String::from("hello"),
        f3: <[_]>::into_vec(::alloc::boxed::box_new([1, 2, 3])),
        f5: String::from("f5"),
        f6: <[_]>::into_vec(::alloc::boxed::box_new([42])),
        f7: 43,
        last: 44,
    };
    let bin = fory1.serialize(&animal).unwrap();
    let obj: Animal2 = fory2.deserialize(&bin).unwrap();
    match (&animal.f1, &obj.f1) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&animal.f3, &obj.f3) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&obj.f4, &String::default()) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&obj.f5, &i8::default()) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&obj.f6, &Vec::<i16>::default()) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&obj.f7, &i16::default()) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
    match (&animal.last, &obj.last) {
        (left_val, right_val) => {
            if !(*left_val == *right_val) {
                let kind = ::core::panicking::AssertKind::Eq;
                ::core::panicking::assert_failed(
                    kind,
                    &*left_val,
                    &*right_val,
                    ::core::option::Option::None,
                );
            }
        }
    };
}
#[rustc_main]
#[coverage(off)]
#[doc(hidden)]
pub fn main() -> () {
    extern crate test;
    test::test_main_static(&[&test_simple])
}
