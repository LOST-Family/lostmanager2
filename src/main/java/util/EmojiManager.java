package util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Manages app emojis for the bot using Discord's Application Emoji REST API
 * Handles downloading PNGs from GitHub, processing them, and uploading as Discord app emojis
 */
public class EmojiManager {
  
  private static final int MAX_BOT_EMOJIS = 1900;
  private static final int MAX_EMOJI_SIZE = 256 * 1024; // 256 KB
  private static final int EMOJI_DIMENSION = 128; // 128x128 pixels
  private static final String BOT_EMOJI_PREFIX = "lm2_"; // Prefix to identify bot-created emojis
  private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
  
  // Cache of emoji names to IDs to avoid repeated API calls
  private static final ConcurrentHashMap<String, String> emojiCache = new ConcurrentHashMap<>();
  
  /**
   * Get or create an app emoji for the given image using Discord's Application Emoji REST API
   * @param imagePath The relative path from image_map.json
   * @param emojiName The base name for the emoji (without prefix)
   * @return The emoji in Discord format <:name:id> or null if failed
   */
  public static String getOrCreateEmoji(String imagePath, String emojiName) {
    if (imagePath == null || imagePath.isEmpty() || emojiName == null || emojiName.isEmpty()) {
      return null;
    }
    
    // Extract folder structure from imagePath to differentiate items with same name
    String folderPrefix = extractFolderPrefix(imagePath);
    
    // Combine folder prefix with emoji name
    String nameWithPath = folderPrefix.isEmpty() ? emojiName : folderPrefix + "_" + emojiName;
    
    // Sanitize emoji name (Discord requires alphanumeric + underscores)
    String sanitizedName = sanitizeEmojiName(nameWithPath);
    String fullEmojiName = BOT_EMOJI_PREFIX + sanitizedName;
    
    // Check cache first
    if (emojiCache.containsKey(fullEmojiName)) {
      String emojiId = emojiCache.get(fullEmojiName);
      return "<:" + fullEmojiName + ":" + emojiId + ">";
    }
    
    // Check if emoji already exists via REST API
    try {
      String existingEmojiId = findExistingEmoji(fullEmojiName);
      if (existingEmojiId != null) {
        emojiCache.put(fullEmojiName, existingEmojiId);
        return "<:" + fullEmojiName + ":" + existingEmojiId + ">";
      }
    } catch (Exception e) {
      System.err.println("Error checking existing emojis: " + e.getMessage());
      // Continue to creation if check fails
    }
    
    // Emoji doesn't exist, create it
    try {
      // Check emoji limit and clean up if needed
      int botEmojiCount = countBotEmojis();
      if (botEmojiCount >= MAX_BOT_EMOJIS) {
        deleteRandomBotEmoji();
      }
      
      // Download and process image
      String imageUrl = ImageMapCache.buildImageUrl(imagePath);
      byte[] processedImage = downloadAndProcessImage(imageUrl);
      
      if (processedImage == null) {
        System.err.println("Failed to process image for emoji: " + fullEmojiName);
        return null;
      }
      
      // Upload as app emoji via REST API
      String newEmojiId = createApplicationEmoji(fullEmojiName, processedImage);
      if (newEmojiId == null) {
        return null;
      }
      
      emojiCache.put(fullEmojiName, newEmojiId);
      
      System.out.println("Created new app emoji: " + fullEmojiName + " (ID: " + newEmojiId + ")");
      
      return "<:" + fullEmojiName + ":" + newEmojiId + ">";
      
    } catch (Exception e) {
      System.err.println("Error creating emoji " + fullEmojiName + ": " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Find an existing application emoji by name using REST API
   * @param emojiName The emoji name to search for
   * @return The emoji ID if found, null otherwise
   */
  private static String findExistingEmoji(String emojiName) {
    try {
      String appId = lostmanager.Bot.getJda().getSelfUser().getApplicationId();
      String token = System.getenv("LOST_MANAGER_TOKEN");
      
      URL url = URI.create(DISCORD_API_BASE + "/applications/" + appId + "/emojis").toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bot " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to list application emojis: HTTP " + responseCode);
        return null;
      }
      
      StringBuilder response = new StringBuilder();
      try (InputStream is = conn.getInputStream()) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
          response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
      }
      
      JSONObject jsonResponse = new JSONObject(response.toString());
      JSONArray items = jsonResponse.getJSONArray("items");
      
      for (int i = 0; i < items.length(); i++) {
        JSONObject emoji = items.getJSONObject(i);
        if (emoji.getString("name").equals(emojiName)) {
          return emoji.getString("id");
        }
      }
      
      return null;
      
    } catch (Exception e) {
      System.err.println("Error finding existing emoji: " + e.getMessage());
      return null;
    }
  }
  
  /**
   * Count bot-created application emojis
   * @return Number of emojis with bot prefix
   */
  private static int countBotEmojis() {
    try {
      String appId = lostmanager.Bot.getJda().getSelfUser().getApplicationId();
      String token = System.getenv("LOST_MANAGER_TOKEN");
      
      URL url = URI.create(DISCORD_API_BASE + "/applications/" + appId + "/emojis").toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bot " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to list application emojis for counting: HTTP " + responseCode);
        return 0;
      }
      
      StringBuilder response = new StringBuilder();
      try (InputStream is = conn.getInputStream()) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
          response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
      }
      
      JSONObject jsonResponse = new JSONObject(response.toString());
      JSONArray items = jsonResponse.getJSONArray("items");
      
      int count = 0;
      for (int i = 0; i < items.length(); i++) {
        JSONObject emoji = items.getJSONObject(i);
        if (emoji.getString("name").startsWith(BOT_EMOJI_PREFIX)) {
          count++;
        }
      }
      
      return count;
      
    } catch (Exception e) {
      System.err.println("Error counting bot emojis: " + e.getMessage());
      return 0;
    }
  }
  
  /**
   * Create an application emoji via REST API
   * @param name The emoji name
   * @param imageData The processed image bytes
   * @return The new emoji ID or null if failed
   */
  private static String createApplicationEmoji(String name, byte[] imageData) {
    try {
      String appId = lostmanager.Bot.getJda().getSelfUser().getApplicationId();
      String token = System.getenv("LOST_MANAGER_TOKEN");
      
      // Encode image as base64 data URI
      String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
      
      // Build JSON payload
      JSONObject payload = new JSONObject();
      payload.put("name", name);
      payload.put("image", base64Image);
      
      URL url = URI.create(DISCORD_API_BASE + "/applications/" + appId + "/emojis").toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Authorization", "Bot " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(15000);
      
      // Send payload
      try (OutputStream os = conn.getOutputStream()) {
        byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
      }
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 201 && responseCode != 200) {
        // Read error response
        StringBuilder errorResponse = new StringBuilder();
        try (InputStream is = conn.getErrorStream()) {
          if (is != null) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
              errorResponse.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
          }
        }
        System.err.println("Failed to create application emoji: HTTP " + responseCode + " - " + errorResponse.toString());
        return null;
      }
      
      // Parse response to get emoji ID
      StringBuilder response = new StringBuilder();
      try (InputStream is = conn.getInputStream()) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
          response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
      }
      
      JSONObject jsonResponse = new JSONObject(response.toString());
      return jsonResponse.getString("id");
      
    } catch (Exception e) {
      System.err.println("Error creating application emoji: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Download image from URL and process it according to requirements
   * - Remove transparent background with 25% tolerance
   * - Crop to content
   * - Resize to 128x128 (centered, maintaining aspect ratio)
   * - Compress to max 256KB
   */
  private static byte[] downloadAndProcessImage(String imageUrl) {
    try {
      // Download image
      URL url = URI.create(imageUrl).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      if (conn.getResponseCode() != 200) {
        System.err.println("Failed to download image: HTTP " + conn.getResponseCode() + " for " + imageUrl);
        return null;
      }
      
      BufferedImage originalImage;
      try (InputStream is = conn.getInputStream()) {
        originalImage = ImageIO.read(is);
      }
      
      if (originalImage == null) {
        System.err.println("Failed to read image from URL: " + imageUrl);
        return null;
      }
      
      // Process image: crop transparent borders with tolerance
      BufferedImage croppedImage = cropTransparentBorders(originalImage, 0.25f);
      
      // Resize to fit within 128x128 while maintaining aspect ratio
      BufferedImage resizedImage = resizeAndCenter(croppedImage, EMOJI_DIMENSION, EMOJI_DIMENSION);
      
      // Compress to PNG with size limit
      return compressImage(resizedImage, MAX_EMOJI_SIZE);
      
    } catch (Exception e) {
      System.err.println("Error processing image from " + imageUrl + ": " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Crop transparent borders from image with tolerance
   * @param image The image to crop
   * @param tolerance Tolerance for transparency (0.0 to 1.0), 0.25 = 25%
   * @return Cropped image
   */
  private static BufferedImage cropTransparentBorders(BufferedImage image, float tolerance) {
    int width = image.getWidth();
    int height = image.getHeight();
    
    int minX = width;
    int minY = height;
    int maxX = 0;
    int maxY = 0;
    
    int toleranceAlpha = (int) (255 * tolerance);
    
    // Find bounding box of non-transparent pixels
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixel = image.getRGB(x, y);
        int alpha = (pixel >> 24) & 0xff;
        
        // If pixel is not transparent (beyond tolerance)
        if (alpha > toleranceAlpha) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    
    // Handle case where image is completely transparent
    if (minX > maxX || minY > maxY) {
      return image;
    }
    
    // Crop to bounding box
    int cropWidth = maxX - minX + 1;
    int cropHeight = maxY - minY + 1;
    
    return image.getSubimage(minX, minY, cropWidth, cropHeight);
  }
  
  /**
   * Resize image to fit within target dimensions while maintaining aspect ratio
   * and center it on a transparent canvas
   * @param image The image to resize
   * @param targetWidth Target width
   * @param targetHeight Target height
   * @return Resized and centered image
   */
  private static BufferedImage resizeAndCenter(BufferedImage image, int targetWidth, int targetHeight) {
    try {
      int originalWidth = image.getWidth();
      int originalHeight = image.getHeight();
      
      // Calculate scaling factor to fit within target dimensions
      double scaleX = (double) targetWidth / originalWidth;
      double scaleY = (double) targetHeight / originalHeight;
      double scale = Math.min(scaleX, scaleY);
      
      int scaledWidth = (int) (originalWidth * scale);
      int scaledHeight = (int) (originalHeight * scale);
      
      // Resize image
      BufferedImage scaledImage = Thumbnails.of(image)
          .size(scaledWidth, scaledHeight)
          .asBufferedImage();
      
      // Create target canvas with transparency
      BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = result.createGraphics();
      
      // Fill with transparent background
      g2d.setColor(new Color(0, 0, 0, 0));
      g2d.fillRect(0, 0, targetWidth, targetHeight);
      
      // Center the scaled image
      int x = (targetWidth - scaledWidth) / 2;
      int y = (targetHeight - scaledHeight) / 2;
      g2d.drawImage(scaledImage, x, y, null);
      g2d.dispose();
      
      return result;
      
    } catch (Exception e) {
      System.err.println("Error resizing image: " + e.getMessage());
      e.printStackTrace();
      return image;
    }
  }
  
  /**
   * Compress image to PNG with size limit
   * @param image The image to compress
   * @param maxSizeBytes Maximum size in bytes
   * @return Compressed image bytes or null if failed
   */
  private static byte[] compressImage(BufferedImage image, int maxSizeBytes) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      
      // Try PNG compression
      ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
      ImageWriteParam writeParam = writer.getDefaultWriteParam();
      
      try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), writeParam);
      }
      writer.dispose();
      
      byte[] result = baos.toByteArray();
      
      // Check size
      if (result.length <= maxSizeBytes) {
        return result;
      }
      
      System.err.println("Warning: Compressed image size (" + result.length + " bytes) exceeds limit (" + maxSizeBytes + " bytes)");
      
      // If still too large, return anyway (Discord will reject it, but we tried)
      return result;
      
    } catch (Exception e) {
      System.err.println("Error compressing image: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Delete a random bot-created emoji to make room for new ones
   */
  private static void deleteRandomBotEmoji() {
    try {
      String appId = lostmanager.Bot.getJda().getSelfUser().getApplicationId();
      String token = System.getenv("LOST_MANAGER_TOKEN");
      
      // Get list of bot emojis
      URL url = URI.create(DISCORD_API_BASE + "/applications/" + appId + "/emojis").toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setRequestProperty("Authorization", "Bot " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to list application emojis for deletion: HTTP " + responseCode);
        return;
      }
      
      StringBuilder response = new StringBuilder();
      try (InputStream is = conn.getInputStream()) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
          response.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
      }
      
      JSONObject jsonResponse = new JSONObject(response.toString());
      JSONArray items = jsonResponse.getJSONArray("items");
      
      // Collect bot emojis
      java.util.List<String[]> botEmojis = new java.util.ArrayList<>();
      for (int i = 0; i < items.length(); i++) {
        JSONObject emoji = items.getJSONObject(i);
        String name = emoji.getString("name");
        if (name.startsWith(BOT_EMOJI_PREFIX)) {
          botEmojis.add(new String[]{emoji.getString("id"), name});
        }
      }
      
      if (botEmojis.isEmpty()) {
        return;
      }
      
      // Select random emoji to delete
      Random random = new Random();
      String[] toDelete = botEmojis.get(random.nextInt(botEmojis.size()));
      String emojiId = toDelete[0];
      String emojiName = toDelete[1];
      
      System.out.println("Deleting emoji to make room: " + emojiName + " (ID: " + emojiId + ")");
      
      // Delete via REST API
      URL deleteUrl = URI.create(DISCORD_API_BASE + "/applications/" + appId + "/emojis/" + emojiId).toURL();
      HttpURLConnection deleteConn = (HttpURLConnection) deleteUrl.openConnection();
      deleteConn.setRequestMethod("DELETE");
      deleteConn.setRequestProperty("Authorization", "Bot " + token);
      deleteConn.setConnectTimeout(10000);
      deleteConn.setReadTimeout(10000);
      
      int deleteResponseCode = deleteConn.getResponseCode();
      if (deleteResponseCode == 204 || deleteResponseCode == 200) {
        emojiCache.remove(emojiName);
        System.out.println("Successfully deleted emoji: " + emojiName);
      } else {
        System.err.println("Failed to delete emoji: HTTP " + deleteResponseCode);
      }
      
    } catch (Exception e) {
      System.err.println("Error deleting random emoji: " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  /**
   * Extract folder structure from image path to create unique emoji names
   * Uses only the first letter (initial) of each folder name to keep emoji names short
   * Example: "/home-base/buildings/wall/wall-1.png" -> "hb_b_w"
   * Example: "/builder-base/buildings/wall/wall-2.png" -> "bb_b_w"
   * 
   * @param imagePath The relative image path (e.g., "/home-base/buildings/wall/wall-1.png")
   * @return The folder structure as underscore-separated initials
   */
  private static String extractFolderPrefix(String imagePath) {
    if (imagePath == null || imagePath.isEmpty()) {
      return "";
    }
    
    // Remove leading slash if present
    String path = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
    
    // Split by slash to get path components
    String[] parts = path.split("/");
    
    // Build folder prefix from initials of all parts except the filename (last part)
    // Stop before the filename
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      
      // Skip "assets" if present (though it shouldn't be in relative paths)
      if (part.equalsIgnoreCase("assets")) {
        continue;
      }
      
      // Build the initials for this folder part
      StringBuilder folderInitials = new StringBuilder();
      
      // Take only the first character (initial) of the folder name
      // Handle multi-word folder names with hyphens by taking first char after each hyphen
      String[] words = part.split("-");
      for (String word : words) {
        if (!word.isEmpty()) {
          // Take first character of each word, convert to lowercase
          folderInitials.append(Character.toLowerCase(word.charAt(0)));
        }
      }
      
      // Add underscore separator between folders (but not before the first folder)
      if (prefix.length() > 0) {
        prefix.append("_");
      }
      prefix.append(folderInitials);
    }
    
    return prefix.toString();
  }
  
  /**
   * Sanitize emoji name for Discord (alphanumeric + underscores only)
   * @param name The name to sanitize
   * @return Sanitized name
   */
  private static String sanitizeEmojiName(String name) {
    // Replace spaces and special characters with underscores
    String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
    
    // Remove consecutive underscores
    sanitized = sanitized.replaceAll("_+", "_");
    
    // Remove leading/trailing underscores
    sanitized = sanitized.replaceAll("^_+|_+$", "");
    
    // Discord emoji names must be 2-32 characters
    if (sanitized.length() < 2) {
      sanitized = "item_" + sanitized;
    }
    // Account for the BOT_EMOJI_PREFIX length (4 chars: "lm2_")
    // Maximum allowed is 32 chars total, so sanitized part should be max 28 chars
    int maxLength = 32 - BOT_EMOJI_PREFIX.length();
    if (sanitized.length() > maxLength) {
      sanitized = sanitized.substring(0, maxLength);
    }
    
    return sanitized.toLowerCase();
  }
  
  /**
   * Clear the emoji cache (useful for testing or refresh)
   */
  public static void clearCache() {
    emojiCache.clear();
  }
}
