package xyz.csongyu.fluentapi;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.client.fluent.Request;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

public class ConcurrentTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8088);

    @Test
    public void testAllHttpStatus200() throws IOException {
        stubFor(get(urlPathMatching("/index/.*")).withHeader("Accept", equalTo("application/json"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"testing-library\": \"WireMock\"}").withFixedDelay(1_000)));

        final Path directory = Paths.get("dir");
        this.download(directory);

        assertEquals(101, Files.walk(directory).filter(Files::isRegularFile).count());
    }

    // @Test(expected = CompletionException.class)
    @Test
    public void testAllHttpStatus404() throws IOException {
        stubFor(get(urlPathMatching("/index/.*")).withHeader("Accept", equalTo("application/json"))
            .willReturn(aResponse().withStatus(404).withFixedDelay(1_000)));

        final Path directory = Paths.get("dir");
        this.download(directory);

        assertEquals(0, Files.walk(directory).filter(Files::isRegularFile).count());
    }

    private void download(final Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.walk(directory).filter(Files::isRegularFile).map(Path::toFile).forEach(File::delete);

        final List<Integer> indexes = Stream.iterate(0, item -> item + 1).limit(101).collect(Collectors.toList());
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        CompletableFuture.allOf(indexes.stream().map(index -> CompletableFuture.supplyAsync(() -> {
            System.out.println(Thread.currentThread().getName() + " | " + index);
            final Request request = Request.Get("http://127.0.0.1:8088/index/" + index).connectTimeout(1_000)
                .socketTimeout(5_000).addHeader("Accept", "application/json");
            try {
                request.execute().saveContent(directory.resolve(index + ".json").toFile());
            } catch (final IOException e) {
                // important
                request.abort();
                return false;
            }
            return Files.exists(Paths.get(index + ".json"));
        }, executorService)).toArray(CompletableFuture[]::new)).orTimeout(30, TimeUnit.SECONDS).join();
    }
}
