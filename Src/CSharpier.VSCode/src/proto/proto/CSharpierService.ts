// Original file: proto/csharpier.proto

import type * as grpc from "@grpc/grpc-js";
import type { MethodDefinition } from "@grpc/proto-loader";
import type {
    FormatFileDto as _proto_FormatFileDto,
    FormatFileDto__Output as _proto_FormatFileDto__Output,
} from "../proto/FormatFileDto";
import type {
    FormatFileResult as _proto_FormatFileResult,
    FormatFileResult__Output as _proto_FormatFileResult__Output,
} from "../proto/FormatFileResult";

export interface CSharpierServiceClient extends grpc.Client {
    FormatFile(
        argument: _proto_FormatFileDto,
        metadata: grpc.Metadata,
        options: grpc.CallOptions,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    FormatFile(
        argument: _proto_FormatFileDto,
        metadata: grpc.Metadata,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    FormatFile(
        argument: _proto_FormatFileDto,
        options: grpc.CallOptions,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    FormatFile(
        argument: _proto_FormatFileDto,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    formatFile(
        argument: _proto_FormatFileDto,
        metadata: grpc.Metadata,
        options: grpc.CallOptions,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    formatFile(
        argument: _proto_FormatFileDto,
        metadata: grpc.Metadata,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    formatFile(
        argument: _proto_FormatFileDto,
        options: grpc.CallOptions,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
    formatFile(
        argument: _proto_FormatFileDto,
        callback: grpc.requestCallback<_proto_FormatFileResult__Output>,
    ): grpc.ClientUnaryCall;
}

export interface CSharpierServiceHandlers extends grpc.UntypedServiceImplementation {
    FormatFile: grpc.handleUnaryCall<_proto_FormatFileDto__Output, _proto_FormatFileResult>;
}

export interface CSharpierServiceDefinition extends grpc.ServiceDefinition {
    FormatFile: MethodDefinition<
        _proto_FormatFileDto,
        _proto_FormatFileResult,
        _proto_FormatFileDto__Output,
        _proto_FormatFileResult__Output
    >;
}
