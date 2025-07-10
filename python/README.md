# Apache Fory™ Python

Fory is a blazingly-fast multi-language serialization framework powered by just-in-time compilation and zero-copy.

## Build Fory Python

```bash
cd python
# Uninstall numpy first so that when we install pyarrow, it will install the correct numpy version automatically.
# For Python versions less than 3.13, numpy 2 is not currently supported.
pip uninstall -y numpy
# Install necessary environment for Python < 3.13.
pip install pyarrow==15.0.0 Cython wheel pytest
# For Python 3.13, pyarrow 18.0.0 is available and requires numpy version greater than 2.
# pip install pyarrow==18.0.0 Cython wheel pytest
pip install -v -e .
```

### Environment Requirements

- python 3.8+

## Testing

```bash
cd python
pytest -v -s .
```

## Code Style

```bash
cd python
pip install ruff
ruff format python
```

## Debug

```bash
cd python
python setup.py develop
```

- Use `cython --cplus -a  pyfory/_serialization.pyx` to produce an annotated HTML file of the source code. Then you can
  analyze interaction between Python objects and Python's C API.
- Read more: <https://cython.readthedocs.io/en/latest/src/userguide/debugging.html>

```bash
FORY_DEBUG=true python setup.py build_ext --inplace
# For linux
cygdb build
```

## Debug with lldb

```bash
lldb
(lldb) target create -- python
(lldb) settings set -- target.run-args "-c" "from pyfory.tests.test_serializer import test_enum; test_enum()"
(lldb) run
(lldb) bt
```
