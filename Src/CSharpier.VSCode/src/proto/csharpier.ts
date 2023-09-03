import type * as grpc from "@grpc/grpc-js";
import type { MessageTypeDefinition } from "@grpc/proto-loader";

// TODO some of these files are generated, do they need to be checked in?

import type {
    CSharpierServiceClient as _proto_CSharpierServiceClient,
    CSharpierServiceDefinition as _proto_CSharpierServiceDefinition,
} from "./proto/CSharpierService";

type SubtypeConstructor<Constructor extends new (...args: any) => any, Subtype> = {
    new (...args: ConstructorParameters<Constructor>): Subtype;
};

export interface ProtoGrpcType {
    proto: {
        CSharpierService: SubtypeConstructor<typeof grpc.Client, _proto_CSharpierServiceClient> & {
            service: _proto_CSharpierServiceDefinition;
        };
        FormatFileDto: MessageTypeDefinition;
        FormatFileResult: MessageTypeDefinition;
    };
}
