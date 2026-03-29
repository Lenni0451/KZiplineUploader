# KZiplineUploader
Tool to upload text, images and files to zipline. Shows a KDE Plasma notification with progress and cancel button.

## Usage
You can download the latest release from my [Jenkins server](https://build.lenni0451.net/job/KZiplineUploader/).

The application will immediately check the clipboard for text, images or files and upload them to the configured zipline instance.\
I recommend to set up a global shortcut for the application, so you can easily upload content from the clipboard.

On first start, the application will create a configuration file in the work directory. You should edit this file and set all required values.
```json
{
    "keep_file_name": true,
    "url": "https://example.com",
    "token": "your_token_here"
}
```
