from PIL import Image


def tiff_to_jpeg(input_path: str) -> tuple[str, str]:
    output_path = input_path.replace(".tiff", ".jpg").replace(".tif", ".jpg")
    with Image.open(input_path) as img:
        img.save(output_path, "JPEG", quality=50)
    return output_path, ".jpg"
