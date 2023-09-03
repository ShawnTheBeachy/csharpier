namespace CSharpier.Cli.Tests;

using System.Diagnostics;
using System.Text;
using CSharpierService.Generated;
using FluentAssertions;
using Grpc.Core;
using NUnit.Framework;

[TestFixture]
public class GrpcTests
{
    // TODO clean up and add more tests
    [Test]
    public async Task Stuff()
    {
        var path = Path.Combine(Directory.GetCurrentDirectory(), "dotnet-csharpier.dll");
        
        var processStartInfo = new ProcessStartInfo("dotnet", $"{path} --named-pipe")
        {
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            UseShellExecute = false,
            CreateNoWindow = true,
            EnvironmentVariables =
            {
                ["DOTNET_NOLOGO"] = "1"
            }
        };

        var process = new Process { StartInfo = processStartInfo };
        process.Start();

        var output = await process.StandardOutput.ReadLineAsync();

        var channel = new Channel("localhost", 50052, ChannelCredentials.Insecure);
        var client = new CSharpierService.CSharpierServiceClient(channel);

        var data = new FormatFileDto { FileName = "test.cs", FileContents = "public class TestClass    { }"};
        var result = await client.FormatFileAsync(data);

        result.FormattedFile.Should().Be("public class TestClass { }");
    }
}
