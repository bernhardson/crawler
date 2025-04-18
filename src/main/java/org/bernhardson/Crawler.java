package org.bernhardson;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {

    private static final Pattern HREF_PATTERN = Pattern.compile("<a\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Semaphore SEMAPHORE = new Semaphore(100); // limit parallel fetches

    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final Set<Link> result = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger pagesVisited = new AtomicInteger();
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final URI baseUri;
    private final int maxDepth;
    private final boolean debug;

    /**
     * Provides functionality to crawl a website.
     * @param baseURI URI to crawl
     * @param maxDepth sets the deepest level of crawling the website's document tree
     * @param debug enables debug out
     */
    public Crawler(URI baseURI, int maxDepth, boolean debug) {
        this.baseUri = baseURI;
        this.debug = debug;
        this.maxDepth = maxDepth;
    }

    /**
     * Starts the crawl process from the root URL defined in the constructor.
     * Launches the root thread and waits for all crawling to complete.
     */
    public void start() throws InterruptedException {
        Log.info("Starting crawl at %s", baseUri.toString());
        // start crawling
        Thread root = Thread.startVirtualThread(() -> crawl(baseUri, 0));
        // wait for threads to finish
        root.join();
        // sort results
        List<Link> sorted = result.stream().sorted(Comparator.comparing(Link::label)).toList();
        Log.info("\nCollected Internal Links (Sorted by Label):");
        // print results
        sorted.forEach((l) -> Log.info("%s -> %s", l.label(), l.href()));
        Log.debug(debug, "Found links: %d", sorted.size());
    }

    /**
     * Orchestrates the crawling process.
     * Fetches response body,
     * extracts and normalizes links,
     * checks if the domain is the same and
     * triggers deeper crawling in case the link has not been visited already.
     * The crawling will stop when the search depth limit is reached
     * or there are no more links to visit inside the website domain.
     *
     * @param uri to request and search for further links
     * @param depth maximum search depth to prevent memory leaking
     */
    private void crawl(URI uri, int depth) {
        Log.debug(debug, "depth %d", depth);
        // stop at max depth
        if (depth > maxDepth) return;
        String body = fetch(uri.toString());
        if (body == null) return;

        // start extracting links using a regex pattern
        Matcher matcher = HREF_PATTERN.matcher(body);
        List<Thread> subtasks = new ArrayList<>();
        // run as long the matcher find href tags
        while (matcher.find()) {
            Link link = extractLink(matcher);
            URI resolved = normalize(uri, link.href());
            if (resolved == null) continue;
            if (!isHttpLink(resolved)) continue;
            if (!isSameDomain(baseUri, resolved)) continue;

            String resolvedHref = resolved.toString();
            Link normalized = new Link(link.label(), resolvedHref);
            result.add(normalized);
            // for each link found we here start another virtual thread
            // starting the same process we just walked through
            if (visited.add(resolvedHref)) {
                int count = pagesVisited.incrementAndGet();
                if (count % 100 == 0) {
                    Log.info("Crawled %d pages", count);
                }
                Thread t = Thread.startVirtualThread(() -> crawl(resolved, depth + 1));
                subtasks.add(t);
            }
        }
        // wait for all threads crawling to finish
        subtasks.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private boolean isHttpLink(URI resolved) {
        if (!ALLOWED_SCHEMES.contains(resolved.getScheme().toLowerCase())) {
            Log.debug(debug, "skipping non-http(s) link: %s", resolved);
            return false;
        }
        return true;
    }

    /**
     * Removes relative links and
     * forms an absolute link we can request via http further down the line
     * @param uri Base URI to prepend to relative links
     * @param href raw link extracted from response body
     * @return URI representation of a http URL
     */
    private URI normalize(URI uri, String href) {
        try {
            URI resolved = uri.resolve(href).normalize();
            return new URI(resolved.getScheme(),
                    resolved.getAuthority(),
                    resolved.getPath(),
                    resolved.getQuery(),
                    null);
        } catch (Exception e) {
            Log.debug(debug,"error normalizing href %s, ex: %s", href, e.getMessage());
            return null;
        }
    }

    private Link extractLink(Matcher matcher) {
        String href = matcher.group(1).replace(" ", "%20");
        String label = matcher.group(2).strip();
        if (label.isEmpty()) {
            return new Link(href, href);
        }
        return new Link(label, href);
    }

    /**
     * Compares two URLs' hosts for equality.
     * @param base root URI to start crawling.
     * @param other Link in base's response body
     * @return true if hosts are equal, else false
     */
    private boolean isSameDomain(URI base, URI other) {
        try {
            URL baseUrl = base.toURL();
            URL otherUrl = other.toURL();
            if(baseUrl.getHost().equalsIgnoreCase(otherUrl.getHost())){
                return true;
            }else{
                Log.debug(debug,"skipping non domain link %s%n", otherUrl.toString());
                return false;
            }
        } catch (MalformedURLException e) {
            //using the exception to skip non URL links
            Log.warn("Malformed  URL %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Send http request and on success returns the response body.
     *
     * @param url to request
     * @return response body
     */
    private String fetch(String url) {
        try {
            Log.debug(debug, "fetching %s", url);
            SEMAPHORE.acquire();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36").build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() > 299 && response.statusCode() < 400) {
                Log.debug(debug, "http warning %d , %s", response.statusCode(), response.body());
            }
            if (response.statusCode() > 399) {
                Log.debug(debug, "http error %d , %s", response.statusCode(), response.body());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.toLowerCase().contains("text/html")) {
                return response.body();
            }
        } catch (IOException | InterruptedException e) {
            Log.warn("fetch exception on %s: %s", url, e.getMessage());
        } finally {
            SEMAPHORE.release();
        }
        return null;
    }

    public static void main(String[] args) {

        long start = System.currentTimeMillis();
        String url="https://example.com/";
        boolean debug = false;
        int maxDepth = 10;

        try {
            for (String arg : args) {
                if (arg.equals("--help")) {
                    System.out.println("""
                            Usage: java Crawler <url> [--depth=n] [--debug] [--help]
                            
                            Options:
                              <url>         The URL to crawl (default: https://example.com/)
                              --depth=n     Limit recursive link depth (default: 10)
                              --debug       Enable debug output
                              --help        Show this help message and exit
                            """);
                    System.exit(0);
                } else if (arg.startsWith("--depth=")) {
                    maxDepth = Integer.parseInt(arg.substring("--depth=".length()));
                    Log.info("max search depth %d", maxDepth);
                } else if (arg.equals("--debug")) {
                    debug = true;
                    Log.info("running in debug mode");
                } else if (!arg.startsWith("--")) {
                    url = arg; // assume first non-flag is the URL
                    if(url.isEmpty()){
                        Log.warn("URL must not be empty.");
                        System.exit(1);
                    }
                }
            }
            URI baseUri = new URI(url);
            new Crawler(baseUri,maxDepth, debug).start();
        } catch (InterruptedException e) {
            Log.warn(e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            Log.warn("Invalid depth value");
            System.exit(1);
        } catch (URISyntaxException e) {
            Log.warn("Invalid URL %s", url);
            Log.debug(debug, "%s", e.getMessage());
        }
        Log.debug(debug, "Runtime: %d seconds", (System.currentTimeMillis() - start) / 1000);
    }
}