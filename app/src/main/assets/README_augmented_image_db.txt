# Augmented Image Database Instructions

## Creating the Database

To create the augmented image database (`augmented_images.imgdb`), use the ARCore `arcoreimg` tool:

### Prerequisites
1. Download ARCore SDK for Unity or use Android Studio ARCore tools
2. Install `arcoreimg` command-line tool from ARCore SDK

### Step-by-Step Instructions

1. **Prepare your landmark images:**
   - Use high-quality images (recommended: 1024x768 or higher)
   - Ensure good contrast and recognizable features
   - Avoid images with repetitive patterns
   - Save as PNG or JPG format

2. **Create image list file (`images.txt`):**
   ```
   landmark1.jpg|landmark1_name
   landmark2.jpg|landmark2_name
   landmark3.jpg|landmark3_name
   ```
   Format: `image_filename|image_name`

3. **Generate the database:**
   ```bash
   arcoreimg build-db --input_image_list_path=images.txt --output_db_path=augmented_images.imgdb
   ```

4. **Evaluate image quality (optional):**
   ```bash
   arcoreimg eval-img --input_image_path=landmark1.jpg
   ```
   Quality score should be 75+ for good tracking.

5. **Place the database:**
   - Copy `augmented_images.imgdb` to `app/src/main/assets/`
   - The app will automatically load it at runtime

### Example Commands

```bash
# Build database from image list
arcoreimg build-db --input_image_list_path=landmark_images.txt --output_db_path=augmented_images.imgdb

# Evaluate single image quality
arcoreimg eval-img --input_image_path=statue_of_liberty.jpg

# Build database from directory (alternative)
arcoreimg build-db --input_images_directory=./landmarks/ --output_db_path=augmented_images.imgdb
```

### Tips for Better Recognition
- Use images with rich texture and detail
- Avoid motion blur, reflections, or extreme lighting
- Test different angles of the same landmark
- Keep image names descriptive and unique

### Troubleshooting
- If database fails to load, check file exists in assets folder
- Verify image quality scores are above 50 (preferably 75+)
- Ensure image list format is correct (filename|name)
- Check ARCore is properly installed on target device
