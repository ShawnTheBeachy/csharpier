using Grpc.Core;

namespace CSharpier.Cli;

using System.IO.Abstractions;
using CSharpierService.Generated;
using Microsoft.Extensions.Logging;

public static class ProtoFormatter
{
    public static Task<int> Pipe(
        SystemConsole console,
        ConsoleLogger logger,
        string? actualConfigPath,
        CancellationToken cancellationToken
    )
    {
        // TODO what about properly shutting this down? it seems to happen when the process is killed
        var server = new Server
        {
            Services = { CSharpierService.BindService(new CSharpierServiceImplementation()) },
            // TODO try out named pipes and be sure to test in linux
            Ports = { new ServerPort("localhost", 50052, ServerCredentials.Insecure) }
        };
        server.Start();

        logger.LogInformation("Started!!");
        Console.ReadKey();

        return Task.FromResult(0);
    }
}

public class CSharpierServiceImplementation : CSharpierService.CSharpierServiceBase
{
    public async override Task<FormatFileResult> FormatFile(
        FormatFileDto formatFileDto,
        ServerCallContext context
    )
    {
        // TODO what about config file and what not?
        // should this be calling the command line formatter? that thing probably does too much at this point
        // maybe finding config files should be split out from that
        var result = await CodeFormatter.FormatAsync(formatFileDto.FileContents);
        
        // TODO what about checking if this actually formatted?
        // could send back any error messages now
        return new FormatFileResult { FormattedFile = result.Code };
    }
}
