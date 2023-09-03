import { ChildProcessWithoutNullStreams, spawn } from "child_process";
import { Logger } from "./Logger";
import { ICSharpierProcess } from "./CSharpierProcess";
import { ProtoGrpcType } from "./proto/csharpier";
import * as grpc from "@grpc/grpc-js";
import * as protoLoader from "@grpc/proto-loader";
import { FormatFileResult } from "./proto/proto/FormatFileResult";
import * as vscode from "vscode";
import { TaskDefinition, TaskRevealKind } from "vscode";
import { CSharpierServiceClient } from "./proto/proto/CSharpierService";

export class CSharpierProcessProto implements ICSharpierProcess {
    private logger: Logger;
    private client: CSharpierServiceClient;

    constructor(logger: Logger, csharpierPath: string, workingDirectory: string) {
        this.logger = logger;

        const definition: TaskDefinition = {
            type: "string",
        };

        let task = new vscode.Task(
            definition,
            vscode.TaskScope.Workspace,
            "csharpier",
            "csharpier",
            // TODO use proper path
            new vscode.ProcessExecution(
                "C:\\projects\\csharpier\\Src\\CSharpier.Cli\\bin\\Debug\\net7.0\\dotnet-csharpier.exe",
                ["--named-pipe"],
                {
                    cwd: workingDirectory,
                    env: { ...process.env, DOTNET_NOLOGO: "1" },
                },
            ),
        );

        task.presentationOptions.reveal = TaskRevealKind.Never;

        vscode.tasks.executeTask(task);

        const host = "0.0.0.0:50052";
        // TODO how do we get this into the build dir? do we need webpack to understand it?
        // TODO i had to copy it there after npm run start, otherwise webpack kills it off
        const packageDefinition = protoLoader.loadSync(__dirname + "/proto/csharpier.proto");
        const proto = grpc.loadPackageDefinition(packageDefinition) as unknown as ProtoGrpcType;

        this.client = new proto.proto.CSharpierService(host, grpc.credentials.createInsecure());
        const deadline = new Date();
        deadline.setSeconds(deadline.getSeconds() + 5);
        this.client.waitForReady(deadline, (error?: Error) => {
            if (error) {
                this.logger.info(`Client connect error: ${error.message}`);
            } else {
                this.logger.debug("Warm CSharpier with initial format");
                // warm by formatting a file twice, the 3rd time is when it gets really fast
                this.formatFile("public class ClassName { }", "Test.cs").then(() => {
                    this.formatFile("public class ClassName { }", "Test.cs");
                });
            }
        });
    }

    formatFile(content: string, filePath: string): Promise<string> {
        // TODO if the client is not connected, then queue these up
        return new Promise<string>(resolve => {
            this.client.formatFile(
                {
                    FileName: filePath,
                    FileContents: content,
                },
                (error?: grpc.ServiceError | null, serverMessage?: FormatFileResult) => {
                    if (error) {
                        this.logger.error(error.message);
                        resolve("");
                    } else if (serverMessage) {
                        this.logger.debug(serverMessage.FormattedFile);
                        resolve(serverMessage.FormattedFile!);
                    }
                },
            );
        });
    }

    dispose() {
        this.client.close();
        // TODO also kill task?
    }
}
