# Burp Repeater URL Renamer

A simple Burp Suite extension that automatically renames Repeater tabs based on the request URL.

## Features

- Automatically renames Repeater tabs to match the URL being requested
- Uses a clean format: `hostname/path`
- Works in real-time as requests are sent

## Installation

1. Download the latest release JAR file
2. Open Burp Suite
3. Go to Extensions tab
4. Click "Add" button
5. Select the downloaded JAR file

## Usage

The extension works automatically:
1. Send a request to Repeater
2. The tab will be automatically renamed to match the URL
3. Format: `hostname/path` (e.g., `example.com/api/endpoint`)

You can also use Ctrl+R while in a Repeater tab to manually extract and display the URL in Burp's extension output.

## Building from Source
