"""
Typed objects for PERO relay: Redis job payloads and PERO API structures.
"""

import os
from typing import Optional

from pydantic import BaseModel, computed_field

from constants import JOB_KEY_PREFIX

# --- Redis queue payloads ---


class IncomingQueueJob(BaseModel):
    """
    Job payload pushed to Redis queue.
    Bucket is fixed, input_key derived from job_id."""

    job_id: str
    ext: str
    engine: int = 1

    @computed_field
    @property
    def img_name(self) -> str:
        """Image name for PERO: {job_id}{ext} e.g. uuid.jpg"""
        return f"{self.job_id}{self.ext}" if self.ext.startswith(".") else f"{self.job_id}.{self.ext}"

    def get_local_img_path(self, base_path: str) -> str:
        return os.path.join(base_path, self.img_name)

    def get_local_txt_path(self, base_path: str) -> str:
        return os.path.join(base_path, f"{self.job_id}.txt")

    def get_local_alto_path(self, base_path: str) -> str:
        return os.path.join(base_path, f"{self.job_id}.alto")

    @computed_field
    @property
    def img_object_key(self) -> str:
        """MinIO object key: {job_id}/input{ext} e.g. uuid/input.jpg"""
        return f"{self.job_id}/input{self.ext}" if self.ext.startswith(".") else f"{self.job_id}/input.{self.ext}"

    @computed_field
    @property
    def txt_object_key(self) -> str:
        """MinIO object key: {job_id}/result.txt"""
        return f"{self.job_id}/result.txt"

    @computed_field
    @property
    def alto_object_key(self) -> str:
        """MinIO object key: {job_id}/result.alto"""
        return f"{self.job_id}/result.alto"

    @computed_field
    @property
    def job_key(self) -> str:
        """Redis key for job status hash, e.g. pero:job:uuid"""
        return f"{JOB_KEY_PREFIX}{self.job_id}"

    @computed_field
    @property
    def content_type(self) -> str:
        if self.ext in (".tiff", ".tif"):
            return "image/tiff"
        if self.ext in (".jpg", ".jpeg"):
            return "image/jpeg"
        if self.ext in (".jp2",):
            return "image/jp2"
        raise ValueError(f"Unknown extension: {self.ext}")


class FinalQueueJob(BaseModel):
    """Queue payload for final queue."""

    job_id: str
    status: str = "done"
    error: Optional[str] = None


# --- PERO API ---


class PeroProcessingRequest(BaseModel):
    """Body sent to PERO post_processing_request endpoint."""

    engine: int
    images: dict[str, None]

    @classmethod
    def for_images(
        cls, engine: int, image_names: list[str]
    ) -> "PeroProcessingRequest":
        return cls(engine=engine, images={name: None for name in image_names})


class PeroProcessingResponse(BaseModel):
    """Response from PERO post_processing_request."""

    request_id: str


class PeroRequestStatus(BaseModel):
    """Response from PERO request_status endpoint."""

    status: str = "failure"
    message: Optional[str] = None
    request_status: Optional[dict] = (
        None  # {image_name: {"state": "PROCESSED", ...}}
    )
