"""
PERO OCR API client: post_processing_request, upload_image,
request_status, download_results.
"""

import os
import posixpath
from time import sleep

import requests
from requests_toolbelt import MultipartEncoder

from models import (
    PeroProcessingRequest,
    PeroProcessingResponse,
    PeroRequestStatus,
)


class PeroClient:
    """Client for PERO OCR API with api-key and base URL."""

    def __init__(self, server_url: str, api_key: str):
        self._base = server_url.rstrip("/") + "/"
        self._api_key = api_key
        self._session = requests.Session()
        self._session.headers["api-key"] = api_key

    def _url(self, *parts: str) -> str:
        return posixpath.join(self._base, *parts)

    def post_processing_request(
        self, engine: int, image_names: list[str]
    ) -> str:
        """Create a processing request. Returns request_id."""
        req = PeroProcessingRequest.for_images(engine, image_names)
        url = self._url("post_processing_request")
        resp = self._session.post(
            url,
            data=req.model_dump_json(),
            headers={"Content-Type": "application/json"},
        )
        if resp.status_code >= 400:
            raise RuntimeError(
                f"post_processing_request failed: {resp.status_code}"
            )
        parsed = PeroProcessingResponse.model_validate(resp.json())
        retries = 5
        while retries > 0:
            status = self.get_request_status(parsed.request_id)
            if status.status == "success":
                return parsed.request_id
            if status.status == "failure":
                raise RuntimeError(status.message or "Request creation failed")
            sleep(15)
            retries -= 1
        raise RuntimeError("post_processing_request: max retries exceeded")

    def upload_image(
        self,
        request_id: str,
        file_name: str,
        image_path: str,
        content_type: str = "image/jpeg",
    ) -> None:
        """Upload an image for a processing request."""
        url = self._url("upload_image", request_id, file_name)
        with open(image_path, "rb") as f:
            m = MultipartEncoder(
                fields={"file": (image_path, f, content_type)}
            )
            resp = self._session.post(
                url,
                data=m,
                headers={"Content-Type": m.content_type},
                timeout=60,
            )
        if resp.status_code >= 400:
            raise RuntimeError(f"upload_image failed: {resp.status_code}")

    def get_request_status(self, request_id: str) -> PeroRequestStatus:
        """Get status of a processing request."""
        url = self._url("request_status", request_id)
        resp = self._session.get(
            url,
            headers={"Content-Type": "application/json"},
            timeout=30,
        )
        if resp.status_code == 200:
            return PeroRequestStatus.model_validate(resp.json())
        return PeroRequestStatus(
            status="failure",
            message=resp.text or f"HTTP {resp.status_code}",
        )

    def download_results(
        self,
        request_id: str,
        file_name: str,
        result_format: str,
        output_path: str,
    ) -> None:
        """Download txt or alto result to a file. Retries on connection errors (e.g. IncompleteRead)."""
        url = self._url(
            "download_results", request_id, file_name, result_format
        )
        out_dir = os.path.dirname(output_path)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)

        last_error = None
        for attempt in range(3):
            try:
                resp = self._session.get(url, timeout=60)
                if resp.status_code != 200:
                    raise RuntimeError(
                        f"download_results failed: {resp.status_code}"
                    )
                with open(output_path, "w", encoding="utf-8") as f:
                    f.write(resp.text)
                return
            except (
                requests.exceptions.ChunkedEncodingError,
                requests.exceptions.ConnectionError,
            ) as e:
                last_error = e
                if attempt < 2:
                    sleep(2 * (attempt + 1))
            except Exception as e:
                err_msg = str(e)
                if "IncompleteRead" in err_msg or "Connection broken" in err_msg:
                    last_error = e
                    if attempt < 2:
                        sleep(2 * (attempt + 1))
                else:
                    raise
        raise last_error

    def close(self) -> None:
        self._session.close()
