#!/bin/bash
set -euo pipefail

REPO="hansd/gcal-gmail-mcp"
INSTALL_DIR="$HOME/.gcal-gmail-mcp"
JAR_NAME="gcal-gmail-mcp.jar"

echo "Installing gcal-gmail-mcp..."
echo ""

# Create install directory
mkdir -p "$INSTALL_DIR"

# Check for Java 21+
NEED_JAVA=false
if ! command -v java &> /dev/null; then
    NEED_JAVA=true
else
    JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
        NEED_JAVA=true
    fi
fi

if [ "$NEED_JAVA" = true ]; then
    echo "Java 21+ is required but not found."
    if command -v brew &> /dev/null; then
        echo "Installing Java 21 via Homebrew..."
        brew install --cask temurin@21
    else
        echo "Installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        echo "Installing Java 21 via Homebrew..."
        brew install --cask temurin@21
    fi
    # Verify installation
    if ! java -version 2>&1 | head -1 | grep -q '"2[1-9]\|"[3-9]'; then
        echo "Error: Java installation may require a new terminal. Close and reopen your terminal, then run this script again."
        exit 1
    fi
fi

# Download latest JAR from GitHub Releases
echo "Downloading latest release..."
DOWNLOAD_URL=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" \
    | grep "browser_download_url.*\.jar" \
    | cut -d '"' -f 4)

if [ -z "$DOWNLOAD_URL" ]; then
    echo "Error: Could not find latest release. Check https://github.com/$REPO/releases"
    exit 1
fi

curl -L -o "$INSTALL_DIR/$JAR_NAME" "$DOWNLOAD_URL"
echo "Downloaded to $INSTALL_DIR/$JAR_NAME"

# Check for OAuth credentials
if [ ! -f "$INSTALL_DIR/gcp-oauth.keys.json" ]; then
    echo ""
    echo "-------------------------------------------------------"
    echo "OAuth credentials not found."
    echo ""
    echo "Get the gcp-oauth.keys.json file from your admin and"
    echo "place it at: $INSTALL_DIR/gcp-oauth.keys.json"
    echo ""
    echo "Then run:"
    echo "  java -jar $INSTALL_DIR/$JAR_NAME auth"
    echo "-------------------------------------------------------"
else
    # Credentials exist — run auth if not already authenticated
    if [ ! -f "$INSTALL_DIR/credentials.json" ]; then
        echo ""
        echo "Running authentication..."
        java -jar "$INSTALL_DIR/$JAR_NAME" auth
    else
        echo "Already authenticated."
    fi
fi

# Configure Claude Code (if installed)
if command -v claude &> /dev/null; then
    echo ""
    echo "Configuring Claude Code..."
    claude mcp add gmail-kotlin --scope user -- java -jar "$INSTALL_DIR/$JAR_NAME"
    echo "Done! Restart Claude Code to use the MCP server."
else
    echo ""
    echo "-------------------------------------------------------"
    echo "To configure Claude Code manually, run:"
    echo "  claude mcp add gmail-kotlin --scope user -- java -jar $INSTALL_DIR/$JAR_NAME"
    echo "-------------------------------------------------------"
fi

echo ""
echo "Installation complete."
