# Burp Repeater URL Renamer

A simple Burp Suite extension that automatically renames Repeater tabs based on the request URL.
This extension was made very quickly has not been tested. It is here to have a rough idea on how to replace the old Repeater tab name with the URL. To be used as a starting point for a more robust extension.
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
2. The tab will be automatically renamed to match the URL - after a request is also present
3. Format: `hostname/path` (e.g., `example.com/api/endpoint`)


## Building from Source
