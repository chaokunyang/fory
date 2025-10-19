use std::any::Any;
use std::sync::{Mutex, MutexGuard, OnceLock};

use fory_core::buffer::{Reader, Writer};
use fory_core::fory::Fory;
use fory_core::resolver::context::{ReadContext, WriteContext};
use fory_core::serializer::struct_::{
    reset_struct_debug_hooks, set_after_read_field_func, set_before_read_field_func,
    set_before_write_field_func,
};

#[derive(fory_derive::ForyObject)]
#[fory_debug]
struct DebugSample {
    a: i32,
    b: String,
}

fn event_log() -> &'static Mutex<Vec<String>> {
    static LOG: OnceLock<Mutex<Vec<String>>> = OnceLock::new();
    LOG.get_or_init(|| Mutex::new(Vec::new()))
}

fn before_write(
    struct_name: &str,
    field_name: &str,
    _field_value: &dyn Any,
    _context: &mut WriteContext,
) {
    event_log()
        .lock()
        .unwrap()
        .push(format!("write:{struct_name}:{field_name}"));
}

fn before_read(struct_name: &str, field_name: &str, _context: &mut ReadContext) {
    event_log()
        .lock()
        .unwrap()
        .push(format!("before_read:{struct_name}:{field_name}"));
}

fn after_read(
    struct_name: &str,
    field_name: &str,
    _field_value: &dyn Any,
    _context: &mut ReadContext,
) {
    event_log()
        .lock()
        .unwrap()
        .push(format!("after_read:{struct_name}:{field_name}"));
}

struct HookGuard {
    _lock: MutexGuard<'static, ()>,
}

impl HookGuard {
    fn new() -> Self {
        static HOOK_LOCK: OnceLock<Mutex<()>> = OnceLock::new();
        let lock = HOOK_LOCK.get_or_init(|| Mutex::new(())).lock().unwrap();
        reset_struct_debug_hooks();
        event_log().lock().unwrap().clear();
        HookGuard { _lock: lock }
    }
}

impl Drop for HookGuard {
    fn drop(&mut self) {
        reset_struct_debug_hooks();
        event_log().lock().unwrap().clear();
    }
}

#[test]
fn debug_hooks_trigger_for_struct() {
    let _guard = HookGuard::new();

    set_before_write_field_func(before_write);
    set_before_read_field_func(before_read);
    set_after_read_field_func(after_read);

    let mut fory = Fory::default();
    fory.register::<DebugSample>(4001).unwrap();
    let sample = DebugSample {
        a: 7,
        b: "debug".to_string(),
    };

    let bytes = fory.serialize(&sample).unwrap();
    let _: DebugSample = fory.deserialize(&bytes).unwrap();

    let entries = event_log().lock().unwrap().clone();
    assert_eq!(
        entries.iter().filter(|e| e.starts_with("write:")).count(),
        2
    );
    assert_eq!(
        entries
            .iter()
            .filter(|e| e.starts_with("before_read:"))
            .count(),
        2
    );
    assert_eq!(
        entries
            .iter()
            .filter(|e| e.starts_with("after_read:"))
            .count(),
        2
    );
    assert!(entries.contains(&"write:DebugSample:a".to_string()));
    assert!(entries.contains(&"before_read:DebugSample:a".to_string()));
    assert!(entries.contains(&"after_read:DebugSample:b".to_string()));

    event_log().lock().unwrap().clear();

    let mut fory_compat = Fory::default().compatible(true);
    fory_compat.register::<DebugSample>(4001).unwrap();
    let writer = Writer::default();
    let mut write_ctx = WriteContext::new_from_fory(writer, &fory_compat);
    fory_compat
        .serialize_with_context(&sample, &mut write_ctx)
        .unwrap();
    let compat_bytes = write_ctx.writer.dump();
    let reader = Reader::new(compat_bytes.as_slice());
    let mut read_ctx = ReadContext::new_from_fory(reader, &fory_compat);
    let _: DebugSample = fory_compat.deserialize_with_context(&mut read_ctx).unwrap();

    let compat_entries = event_log().lock().unwrap().clone();
    assert!(
        compat_entries
            .iter()
            .filter(|e| e.starts_with("write:"))
            .count()
            >= 2
    );
    assert!(
        compat_entries
            .iter()
            .filter(|e| e.starts_with("before_read:"))
            .count()
            >= 2
    );
    assert!(
        compat_entries
            .iter()
            .filter(|e| e.starts_with("after_read:"))
            .count()
            >= 2
    );
}
