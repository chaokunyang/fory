# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""JavaScript gRPC service generator helpers."""

from typing import Dict, List, Set, Union as TypingUnion

from fory_compiler.generators.base import GeneratedFile
from fory_compiler.generators.services.base import StreamingMode, streaming_mode
from fory_compiler.ir.ast import Message, NamedType, RpcMethod, Service, Union


RootType = TypingUnion[Message, Union]


class JavaScriptServiceGeneratorMixin:
    """Generates JavaScript Node.js and gRPC-Web service companions."""

    def generate_services(self) -> List[GeneratedFile]:
        local_services = [
            service
            for service in self.schema.services
            if not self.is_imported_type(service)
        ]
        if not local_services:
            return []
        self._validate_javascript_grpc_services(local_services)
        files: List[GeneratedFile] = []
        if self.options.grpc:
            files.append(self._generate_node_grpc_module(local_services))
        if getattr(self.options, "grpc_web", False):
            self._validate_grpc_web_services(local_services)
            files.append(self._generate_grpc_web_module(local_services))
        return files

    def _validate_javascript_grpc_services(self, services: List[Service]) -> None:
        used_exports: Dict[str, str] = {}
        for service in services:
            service_exports = [
                self._service_constant_name(service),
                self._method_paths_constant_name(service),
                f"{self.to_pascal_case(service.name)}Handlers",
                f"create{self.to_pascal_case(service.name)}ServiceDefinition",
                f"add{self.to_pascal_case(service.name)}Service",
                f"{self.to_pascal_case(service.name)}Client",
                f"create{self.to_pascal_case(service.name)}Client",
                f"{self.to_pascal_case(service.name)}WebClient",
                f"{self.to_pascal_case(service.name)}WebPromiseClient",
                f"create{self.to_pascal_case(service.name)}WebClient",
                f"create{self.to_pascal_case(service.name)}WebPromiseClient",
            ]
            for export in service_exports:
                previous = used_exports.get(export)
                if previous is not None:
                    raise ValueError(
                        "JavaScript gRPC service export collision: "
                        f"{previous} and {service.name} both generate {export}"
                    )
                used_exports[export] = service.name

            used_methods: Dict[str, str] = {}
            used_paths: Dict[str, str] = {}
            for method in service.methods:
                method_name = self._method_name(method)
                previous_method = used_methods.get(method_name)
                if previous_method is not None:
                    raise ValueError(
                        f"JavaScript gRPC method name collision in service "
                        f"{service.name}: {previous_method} and {method.name} "
                        f"both generate {method_name}"
                    )
                used_methods[method_name] = method.name

                path_key = self._method_path_key(method)
                previous_path = used_paths.get(path_key)
                if previous_path is not None:
                    raise ValueError(
                        f"JavaScript gRPC method path key collision in service "
                        f"{service.name}: {previous_path} and {method.name} "
                        f"both generate {path_key}"
                    )
                used_paths[path_key] = method.name

                self._resolve_rpc_root(method.request_type)
                self._resolve_rpc_root(method.response_type)

    def _validate_grpc_web_services(self, services: List[Service]) -> None:
        for service in services:
            for method in service.methods:
                mode = streaming_mode(method)
                if mode in (
                    StreamingMode.CLIENT_STREAMING,
                    StreamingMode.BIDIRECTIONAL,
                ):
                    raise ValueError(
                        "JavaScript gRPC-Web does not support client-streaming "
                        f"or bidirectional methods: {service.name}.{method.name}"
                    )

    def _generate_node_grpc_module(self, services: List[Service]) -> GeneratedFile:
        lines = self._service_file_prelude("node")
        lines.append('import * as grpc from "@grpc/grpc-js";')
        lines.extend(self._generate_model_imports(services))
        lines.append("")
        lines.extend(self._generate_shared_codec_bindings(services))
        lines.append("")
        lines.extend(self._generate_node_buffer_helper())
        lines.append("")
        for service in services:
            lines.extend(self._generate_node_service(service))
            lines.append("")
        return GeneratedFile(
            path=f"{self.get_module_name()}_grpc.ts",
            content="\n".join(lines).rstrip() + "\n",
        )

    def _generate_grpc_web_module(self, services: List[Service]) -> GeneratedFile:
        lines = self._service_file_prelude("web")
        lines.append('import * as grpcWeb from "grpc-web";')
        lines.extend(self._generate_model_imports(services))
        lines.append("")
        lines.extend(
            [
                'export type ForyGrpcWebWireFormat = "grpcweb" | "grpcwebtext";',
                "",
                "export interface ForyGrpcWebClientOptions {",
                "  credentials?: null | { [index: string]: string };",
                "  unaryInterceptors?: grpcWeb.UnaryInterceptor<unknown, unknown>[];",
                "  streamInterceptors?: grpcWeb.StreamInterceptor<unknown, unknown>[];",
                "  suppressCorsPreflight?: boolean;",
                "  withCredentials?: boolean;",
                "  wireFormat?: ForyGrpcWebWireFormat;",
                "}",
                "",
            ]
        )
        lines.extend(self._generate_shared_codec_bindings(services))
        lines.append("")
        lines.extend(self._generate_grpc_web_type_bindings(services))
        lines.append("")
        lines.extend(self._generate_grpc_web_helpers(services))
        lines.append("")
        for service in services:
            lines.extend(self._generate_grpc_web_service(service))
            lines.append("")
        return GeneratedFile(
            path=f"{self.get_module_name()}_grpc_web.ts",
            content="\n".join(lines).rstrip() + "\n",
        )

    def _service_file_prelude(self, target: str) -> List[str]:
        lines = [self.get_license_header("//"), ""]
        lines.append(
            f"// {target} gRPC bindings for package: {self.package or 'default'}"
        )
        return lines

    def _generate_model_imports(self, services: List[Service]) -> List[str]:
        current_types: Set[str] = set()
        imported_types: Dict[str, Set[str]] = {}
        for root in self._service_roots(services):
            type_name = self._ts_type_name(root)
            if self.is_imported_type(root):
                module = self._module_path_for_type(root)
                imported_types.setdefault(module, set()).add(type_name)
                if isinstance(root, Union):
                    imported_types[module].add(f"{type_name}Case")
            else:
                current_types.add(type_name)
                if isinstance(root, Union):
                    current_types.add(f"{type_name}Case")

        lines: List[str] = []
        current_imports = ["getFory", *sorted(current_types)]
        lines.append(
            f'import {{ {", ".join(current_imports)} }} from "./{self.get_module_name()}";'
        )
        for module, names in sorted(imported_types.items()):
            lines.append(f'import {{ {", ".join(sorted(names))} }} from "{module}";')
        return lines

    def _service_roots(self, services: List[Service]) -> List[RootType]:
        roots: List[RootType] = []
        seen: Set[int] = set()
        for service in services:
            for method in service.methods:
                for named in (method.request_type, method.response_type):
                    root = self._resolve_rpc_root(named)
                    if id(root) in seen:
                        continue
                    seen.add(id(root))
                    roots.append(root)
        return roots

    def _resolve_rpc_root(self, named_type: NamedType) -> RootType:
        resolved = self._resolve_named_type(named_type.name)
        if isinstance(resolved, (Message, Union)):
            return resolved
        raise ValueError(
            f"JavaScript gRPC root type {named_type.name} must resolve to a message or union"
        )

    def _module_path_for_type(self, type_def: RootType) -> str:
        location = getattr(type_def, "location", None)
        file_path = getattr(location, "file", None) if location else None
        imported_schema = self._load_schema(file_path) if file_path else None
        if imported_schema is None:
            return f"./{self.get_module_name()}"
        return f"./{self._module_name_for_schema(imported_schema)}"

    def _ts_type_name(self, type_def: RootType) -> str:
        return self._ts_type_names.get(
            id(type_def), self.safe_type_identifier(type_def.name)
        )

    def _codec_var(self, type_def: RootType) -> str:
        return f"{self.safe_member_name(self._ts_type_name(type_def).replace('.', '_'))}Codec"

    def _serializer_name(self, type_def: RootType) -> str:
        return f"serialize{self.to_pascal_case(self._ts_type_name(type_def).replace('.', '_'))}"

    def _deserializer_name(self, type_def: RootType) -> str:
        return f"deserialize{self.to_pascal_case(self._ts_type_name(type_def).replace('.', '_'))}"

    def _grpc_web_type_name(self, type_def: RootType) -> str:
        return (
            f"{self.safe_member_name(self._ts_type_name(type_def).replace('.', '_'))}"
            "GrpcWebType"
        )

    def _root_identity_expr(self, type_def: RootType) -> str:
        kind = "union" if isinstance(type_def, Union) else "struct"
        if self.should_register_by_id(type_def):
            return f'{{ kind: "{kind}", typeId: {type_def.type_id} }}'
        namespace = self._get_type_package(type_def)
        type_name = self._qualified_type_names.get(id(type_def), type_def.name)
        return (
            f'{{ kind: "{kind}", namespace: "{namespace}", typeName: "{type_name}" }}'
        )

    def _generate_shared_codec_bindings(self, services: List[Service]) -> List[str]:
        lines = ["const FORY = getFory();"]
        for root in self._service_roots(services):
            ts_type = self._ts_type_name(root)
            codec = self._codec_var(root)
            identity = self._root_identity_expr(root)
            lines.append(f"const {codec} = FORY.getRootCodec<{ts_type}>({identity});")
            lines.append(
                f"const {self._serializer_name(root)} = (value: {ts_type}) => "
                f"{codec}.serialize(value);"
            )
            lines.append(
                f"const {self._deserializer_name(root)} = (bytes: Uint8Array) => "
                f"{codec}.deserialize(bytes) as {ts_type};"
            )
        return lines

    def _generate_node_buffer_helper(self) -> List[str]:
        return [
            "const toGrpcBuffer = (bytes: Uint8Array): Buffer => {",
            "  if (Buffer.isBuffer(bytes)) {",
            "    return bytes;",
            "  }",
            "  return Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength);",
            "};",
        ]

    def _generate_node_service(self, service: Service) -> List[str]:
        lines: List[str] = []
        service_name = self.to_pascal_case(service.name)
        lines.append(
            f'export const {self._service_constant_name(service)} = "{self.get_grpc_service_name(service)}";'
        )
        lines.append(f"export const {self._method_paths_constant_name(service)} = {{")
        for method in service.methods:
            lines.append(
                f'  {self._method_path_key(method)}: "{self.get_grpc_method_path(service, method)}",'
            )
        lines.append("} as const;")
        lines.append("")
        lines.extend(self._generate_node_handlers(service))
        lines.append("")
        lines.extend(self._generate_node_service_definition(service))
        lines.append("")
        lines.append(
            f"export function add{service_name}Service(server: grpc.Server, "
            f"handlers: {service_name}Handlers): void {{"
        )
        lines.append(
            f"  server.addService(create{service_name}ServiceDefinition(), handlers);"
        )
        lines.append("}")
        lines.append("")
        lines.extend(self._generate_node_client(service))
        return lines

    def _generate_node_handlers(self, service: Service) -> List[str]:
        lines = [
            f"export interface {self.to_pascal_case(service.name)}Handlers extends grpc.UntypedServiceImplementation {{"
        ]
        for method in service.methods:
            req = self._ts_type_name(self._resolve_rpc_root(method.request_type))
            res = self._ts_type_name(self._resolve_rpc_root(method.response_type))
            handler_type = {
                StreamingMode.UNARY: "grpc.handleUnaryCall",
                StreamingMode.SERVER_STREAMING: "grpc.handleServerStreamingCall",
                StreamingMode.CLIENT_STREAMING: "grpc.handleClientStreamingCall",
                StreamingMode.BIDIRECTIONAL: "grpc.handleBidiStreamingCall",
            }[streaming_mode(method)]
            lines.append(
                f"  {self._method_name(method)}: {handler_type}<{req}, {res}>;"
            )
        lines.append("}")
        return lines

    def _generate_node_service_definition(self, service: Service) -> List[str]:
        service_name = self.to_pascal_case(service.name)
        lines = [
            f"export function create{service_name}ServiceDefinition(): "
            "grpc.ServiceDefinition<grpc.UntypedServiceImplementation> {",
            "  return {",
        ]
        for method in service.methods:
            request_root = self._resolve_rpc_root(method.request_type)
            response_root = self._resolve_rpc_root(method.response_type)
            mode = streaming_mode(method)
            lines.append(f"    {self._method_name(method)}: {{")
            lines.append(
                f"      path: {self._method_paths_constant_name(service)}.{self._method_path_key(method)},"
            )
            lines.append(
                f"      requestStream: {'true' if method.client_streaming else 'false'},"
            )
            lines.append(
                f"      responseStream: {'true' if method.server_streaming else 'false'},"
            )
            lines.append(
                f"      requestSerialize: (value: {self._ts_type_name(request_root)}) => "
                f"toGrpcBuffer({self._serializer_name(request_root)}(value)),"
            )
            lines.append(
                f"      requestDeserialize: {self._deserializer_name(request_root)},"
            )
            lines.append(
                f"      responseSerialize: (value: {self._ts_type_name(response_root)}) => "
                f"toGrpcBuffer({self._serializer_name(response_root)}(value)),"
            )
            lines.append(
                f"      responseDeserialize: {self._deserializer_name(response_root)},"
            )
            lines.append(f'      originalName: "{self._method_name(method)}",')
            lines.append("    },")
            if mode is None:
                raise AssertionError("unreachable")
        lines.append("  };")
        lines.append("}")
        return lines

    def _generate_node_client(self, service: Service) -> List[str]:
        service_name = self.to_pascal_case(service.name)
        lines = [f"export class {service_name}Client extends grpc.Client {{"]
        lines.extend(
            [
                "  constructor(",
                "    address: string,",
                "    credentials: grpc.ChannelCredentials,",
                "    options?: Partial<grpc.ClientOptions>,",
                "  ) {",
                "    super(address, credentials, options);",
                "  }",
                "",
            ]
        )
        for index, method in enumerate(service.methods):
            lines.extend(self._generate_node_client_method(service, method))
            if index != len(service.methods) - 1:
                lines.append("")
        lines.append("}")
        lines.append("")
        lines.append(
            f"export function create{service_name}Client("
            "address: string, credentials: grpc.ChannelCredentials, "
            f"options?: Partial<grpc.ClientOptions>): {service_name}Client {{"
        )
        lines.append(
            f"  return new {service_name}Client(address, credentials, options);"
        )
        lines.append("}")
        return lines

    def _generate_node_client_method(
        self, service: Service, method: RpcMethod
    ) -> List[str]:
        mode = streaming_mode(method)
        req = self._ts_type_name(self._resolve_rpc_root(method.request_type))
        res = self._ts_type_name(self._resolve_rpc_root(method.response_type))
        name = self._method_name(method)
        path = f"{self._method_paths_constant_name(service)}.{self._method_path_key(method)}"
        req_ser = self._serializer_name(self._resolve_rpc_root(method.request_type))
        res_deser = self._deserializer_name(
            self._resolve_rpc_root(method.response_type)
        )
        lines: List[str] = []
        if mode is StreamingMode.UNARY:
            lines.extend(
                [
                    f"  {name}(request: {req}, callback: grpc.requestCallback<{res}>): grpc.ClientUnaryCall;",
                    f"  {name}(request: {req}, metadata: grpc.Metadata, callback: grpc.requestCallback<{res}>): grpc.ClientUnaryCall;",
                    f"  {name}(request: {req}, metadata: grpc.Metadata, options: grpc.CallOptions, callback: grpc.requestCallback<{res}>): grpc.ClientUnaryCall;",
                    f"  {name}(",
                    f"    request: {req},",
                    f"    metadataOrCallback: grpc.Metadata | grpc.requestCallback<{res}>,",
                    f"    optionsOrCallback?: grpc.CallOptions | grpc.requestCallback<{res}>,",
                    f"    callback?: grpc.requestCallback<{res}>,",
                    "  ): grpc.ClientUnaryCall {",
                    "    const metadata = metadataOrCallback instanceof grpc.Metadata",
                    "      ? metadataOrCallback",
                    "      : new grpc.Metadata();",
                    '    const options = optionsOrCallback && typeof optionsOrCallback !== "function"',
                    "      ? optionsOrCallback",
                    "      : {};",
                    '    const responseCallback = (typeof metadataOrCallback === "function"',
                    "      ? metadataOrCallback",
                    '      : typeof optionsOrCallback === "function"',
                    "        ? optionsOrCallback",
                    "        : callback)!;",
                    "    return this.makeUnaryRequest(",
                    f"      {path},",
                    f"      (value: {req}) => toGrpcBuffer({req_ser}(value)),",
                    f"      {res_deser},",
                    "      request,",
                    "      metadata,",
                    "      options,",
                    "      responseCallback,",
                    "    );",
                    "  }",
                ]
            )
        elif mode is StreamingMode.SERVER_STREAMING:
            lines.extend(
                [
                    f"  {name}(request: {req}, metadata?: grpc.Metadata, options?: grpc.CallOptions): grpc.ClientReadableStream<{res}> {{",
                    "    return this.makeServerStreamRequest(",
                    f"      {path},",
                    f"      (value: {req}) => toGrpcBuffer({req_ser}(value)),",
                    f"      {res_deser},",
                    "      request,",
                    "      metadata ?? new grpc.Metadata(),",
                    "      options,",
                    "    );",
                    "  }",
                ]
            )
        elif mode is StreamingMode.CLIENT_STREAMING:
            lines.extend(
                [
                    f"  {name}(callback: grpc.requestCallback<{res}>): grpc.ClientWritableStream<{req}>;",
                    f"  {name}(metadata: grpc.Metadata, callback: grpc.requestCallback<{res}>): grpc.ClientWritableStream<{req}>;",
                    f"  {name}(metadata: grpc.Metadata, options: grpc.CallOptions, callback: grpc.requestCallback<{res}>): grpc.ClientWritableStream<{req}>;",
                    f"  {name}(",
                    f"    metadataOrCallback: grpc.Metadata | grpc.requestCallback<{res}>,",
                    f"    optionsOrCallback?: grpc.CallOptions | grpc.requestCallback<{res}>,",
                    f"    callback?: grpc.requestCallback<{res}>,",
                    f"  ): grpc.ClientWritableStream<{req}> {{",
                    "    const metadata = metadataOrCallback instanceof grpc.Metadata",
                    "      ? metadataOrCallback",
                    "      : new grpc.Metadata();",
                    '    const options = optionsOrCallback && typeof optionsOrCallback !== "function"',
                    "      ? optionsOrCallback",
                    "      : {};",
                    '    const responseCallback = (typeof metadataOrCallback === "function"',
                    "      ? metadataOrCallback",
                    '      : typeof optionsOrCallback === "function"',
                    "        ? optionsOrCallback",
                    "        : callback)!;",
                    "    return this.makeClientStreamRequest(",
                    f"      {path},",
                    f"      (value: {req}) => toGrpcBuffer({req_ser}(value)),",
                    f"      {res_deser},",
                    "      metadata,",
                    "      options,",
                    "      responseCallback,",
                    "    );",
                    "  }",
                ]
            )
        else:
            lines.extend(
                [
                    f"  {name}(metadata?: grpc.Metadata, options?: grpc.CallOptions): grpc.ClientDuplexStream<{req}, {res}> {{",
                    "    return this.makeBidiStreamRequest(",
                    f"      {path},",
                    f"      (value: {req}) => toGrpcBuffer({req_ser}(value)),",
                    f"      {res_deser},",
                    "      metadata ?? new grpc.Metadata(),",
                    "      options,",
                    "    );",
                    "  }",
                ]
            )
        return lines

    def _generate_grpc_web_type_bindings(self, services: List[Service]) -> List[str]:
        lines = [
            "type GrpcWebMessageType<T> = new (...args: unknown[]) => T;",
            "type GrpcWebUnaryClientBase = grpcWeb.GrpcWebClientBase & {",
            "  unaryCall<REQ, RESP>(",
            "    method: string,",
            "    request: REQ,",
            "    metadata: grpcWeb.Metadata,",
            "    methodDescriptor: grpcWeb.MethodDescriptor<REQ, RESP>,",
            "    options?: grpcWeb.PromiseCallOptions,",
            "  ): Promise<RESP>;",
            "};",
        ]
        for root in self._service_roots(services):
            lines.append(
                f"const {self._grpc_web_type_name(root)} = "
                f"Object as unknown as GrpcWebMessageType<{self._ts_type_name(root)}>;"
            )
        return lines

    def _generate_grpc_web_helpers(self, services: List[Service]) -> List[str]:
        has_server_streaming = any(
            method.server_streaming
            for service in services
            for method in service.methods
        )
        default_format = "grpcwebtext" if has_server_streaming else "grpcweb"
        lines = [
            f'const DEFAULT_WIRE_FORMAT: ForyGrpcWebWireFormat = "{default_format}";',
            "",
            "const toGrpcWebOptions = (",
            "  options?: ForyGrpcWebClientOptions,",
            "): grpcWeb.GrpcWebClientBaseOptions => {",
            "  const wireFormat = options?.wireFormat ?? DEFAULT_WIRE_FORMAT;",
        ]
        if has_server_streaming:
            lines.extend(
                [
                    '  if (wireFormat === "grpcweb") {',
                    '    throw new Error("grpcweb binary mode does not support server streaming");',
                    "  }",
                ]
            )
        lines.extend(
            [
                "  return {",
                '    format: wireFormat === "grpcwebtext" ? "text" : "binary",',
                "    unaryInterceptors: options?.unaryInterceptors,",
                "    streamInterceptors: options?.streamInterceptors,",
                "    suppressCorsPreflight: options?.suppressCorsPreflight,",
                "    withCredentials: options?.withCredentials,",
                "  };",
                "};",
            ]
        )
        return lines

    def _generate_grpc_web_service(self, service: Service) -> List[str]:
        lines: List[str] = []
        lines.append(
            f'export const {self._service_constant_name(service)} = "{self.get_grpc_service_name(service)}";'
        )
        lines.append(f"export const {self._method_paths_constant_name(service)} = {{")
        for method in service.methods:
            lines.append(
                f'  {self._method_path_key(method)}: "{self.get_grpc_method_path(service, method)}",'
            )
        lines.append("} as const;")
        lines.append("")
        for method in service.methods:
            lines.extend(self._generate_grpc_web_descriptor(service, method))
            lines.append("")
        lines.extend(self._generate_grpc_web_callback_client(service))
        lines.append("")
        lines.extend(self._generate_grpc_web_promise_client(service))
        return lines

    def _generate_grpc_web_descriptor(
        self, service: Service, method: RpcMethod
    ) -> List[str]:
        request_root = self._resolve_rpc_root(method.request_type)
        response_root = self._resolve_rpc_root(method.response_type)
        service_name = self.to_pascal_case(service.name)
        method_name = self.to_pascal_case(method.name)
        descriptor = f"methodDescriptor{service_name}{method_name}"
        method_type = "SERVER_STREAMING" if method.server_streaming else "UNARY"
        return [
            f"const {descriptor} = new grpcWeb.MethodDescriptor<",
            f"  {self._ts_type_name(request_root)},",
            f"  {self._ts_type_name(response_root)}",
            ">(",
            f"  {self._method_paths_constant_name(service)}.{self._method_path_key(method)},",
            f"  grpcWeb.MethodType.{method_type},",
            f"  {self._grpc_web_type_name(request_root)},",
            f"  {self._grpc_web_type_name(response_root)},",
            f"  {self._serializer_name(request_root)},",
            f"  {self._deserializer_name(response_root)},",
            ");",
        ]

    def _generate_grpc_web_callback_client(self, service: Service) -> List[str]:
        service_name = self.to_pascal_case(service.name)
        lines = [
            f"export class {service_name}WebClient {{",
            "  private readonly client: grpcWeb.GrpcWebClientBase;",
            "  private readonly hostname: string;",
            "",
            "  constructor(",
            "    hostname: string,",
            "    credentials?: null | { [index: string]: string },",
            "    options?: ForyGrpcWebClientOptions,",
            "  ) {",
            "    this.client = new grpcWeb.GrpcWebClientBase(toGrpcWebOptions(options));",
            '    this.hostname = hostname.replace(/\\/+$/, "");',
            "    void credentials;",
            "  }",
            "",
        ]
        for index, method in enumerate(service.methods):
            lines.extend(self._generate_grpc_web_callback_method(service, method))
            if index != len(service.methods) - 1:
                lines.append("")
        lines.append("}")
        lines.append("")
        lines.append(
            f"export function create{service_name}WebClient("
            "hostname: string, options?: ForyGrpcWebClientOptions"
            f"): {service_name}WebClient {{"
        )
        lines.append(
            f"  return new {service_name}WebClient(hostname, options?.credentials ?? null, options);"
        )
        lines.append("}")
        return lines

    def _generate_grpc_web_callback_method(
        self, service: Service, method: RpcMethod
    ) -> List[str]:
        request_root = self._resolve_rpc_root(method.request_type)
        response_root = self._resolve_rpc_root(method.response_type)
        req = self._ts_type_name(request_root)
        res = self._ts_type_name(response_root)
        descriptor = f"methodDescriptor{self.to_pascal_case(service.name)}{self.to_pascal_case(method.name)}"
        name = self._method_name(method)
        if method.server_streaming:
            return [
                f"  {name}(request: {req}, metadata?: grpcWeb.Metadata): grpcWeb.ClientReadableStream<{res}> {{",
                "    return this.client.serverStreaming(",
                f"      this.hostname + {self._method_paths_constant_name(service)}.{self._method_path_key(method)},",
                "      request,",
                "      metadata ?? {},",
                f"      {descriptor},",
                "    );",
                "  }",
            ]
        return [
            f"  {name}(",
            f"    request: {req},",
            "    metadata: grpcWeb.Metadata | null,",
            f"    callback: (error: grpcWeb.RpcError, response: {res}) => void,",
            f"  ): grpcWeb.ClientReadableStream<{res}> {{",
            "    return this.client.rpcCall(",
            f"      this.hostname + {self._method_paths_constant_name(service)}.{self._method_path_key(method)},",
            "      request,",
            "      metadata ?? {},",
            f"      {descriptor},",
            "      callback,",
            "    );",
            "  }",
        ]

    def _generate_grpc_web_promise_client(self, service: Service) -> List[str]:
        service_name = self.to_pascal_case(service.name)
        lines = [
            f"export class {service_name}WebPromiseClient {{",
            "  private readonly client: GrpcWebUnaryClientBase;",
            "  private readonly hostname: string;",
            "",
            "  constructor(",
            "    hostname: string,",
            "    credentials?: null | { [index: string]: string },",
            "    options?: ForyGrpcWebClientOptions,",
            "  ) {",
            "    this.client = new grpcWeb.GrpcWebClientBase(toGrpcWebOptions(options)) as GrpcWebUnaryClientBase;",
            '    this.hostname = hostname.replace(/\\/+$/, "");',
            "    void credentials;",
            "  }",
            "",
        ]
        promise_methods = [
            method for method in service.methods if not method.server_streaming
        ]
        for index, method in enumerate(promise_methods):
            lines.extend(self._generate_grpc_web_promise_method(service, method))
            if index != len(promise_methods) - 1:
                lines.append("")
        lines.append("}")
        lines.append("")
        lines.append(
            f"export function create{service_name}WebPromiseClient("
            "hostname: string, options?: ForyGrpcWebClientOptions"
            f"): {service_name}WebPromiseClient {{"
        )
        lines.append(
            f"  return new {service_name}WebPromiseClient(hostname, options?.credentials ?? null, options);"
        )
        lines.append("}")
        return lines

    def _generate_grpc_web_promise_method(
        self, service: Service, method: RpcMethod
    ) -> List[str]:
        request_root = self._resolve_rpc_root(method.request_type)
        response_root = self._resolve_rpc_root(method.response_type)
        req = self._ts_type_name(request_root)
        res = self._ts_type_name(response_root)
        descriptor = f"methodDescriptor{self.to_pascal_case(service.name)}{self.to_pascal_case(method.name)}"
        name = self._method_name(method)
        return [
            f"  {name}(",
            f"    request: {req},",
            "    metadata?: grpcWeb.Metadata,",
            "    options?: grpcWeb.PromiseCallOptions,",
            f"  ): Promise<{res}> {{",
            "    return this.client.unaryCall(",
            f"      this.hostname + {self._method_paths_constant_name(service)}.{self._method_path_key(method)},",
            "      request,",
            "      metadata ?? {},",
            f"      {descriptor},",
            "      options,",
            "    );",
            "  }",
        ]

    def _service_constant_name(self, service: Service) -> str:
        return f"{self.to_upper_snake_case(service.name)}_SERVICE_NAME"

    def _method_paths_constant_name(self, service: Service) -> str:
        return f"{self.to_upper_snake_case(service.name)}_METHOD_PATHS"

    def _method_name(self, method: RpcMethod) -> str:
        return self.safe_identifier(self.to_camel_case(method.name))

    def _method_path_key(self, method: RpcMethod) -> str:
        return self.safe_identifier(f"{self._method_name(method)}Path")
