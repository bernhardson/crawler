# Java Link Crawler

A simple website crawler written in Java 21+ using virtual threads. It recursively finds internal links on a given website and prints them sorted by label.

## Features

- Crawls internal links on the same domain
- Ignores external and non-HTTP links (e.g., `mailto:`)
- Limits crawl depth via `--depth`
- Debug output with `--debug`

## Requirements

- Java 21 or newer

## Build

```bash
chmod +x build.sh
./build.sh
```

This compiles the source files and creates an executable `crawler.jar` in the project root.

## Run

```bash
java -jar crawler.jar <url> [--depth=n] [--debug] [--help]
```

### Examples

```bash
java -jar crawler.jar https://example.com
java -jar crawler.jar https://example.com --depth=3
java -jar crawler.jar https://example.com --debug
java -jar crawler.jar --help
```

## Sample Output

```
Collected Internal Links (Sorted by Label):
Home -> https://example.com
News -> https://example.com/news
...
Found links: 42
```

## Author
Ulf Seiberth
