# main.py
# 실행: uvicorn main:app --host 0.0.0.0 --port 8000

# ==========================================
# 1. Enum / Import / 환경 변수 로딩
# ==========================================
from enum import Enum
from typing import Optional, List, Any

import os, base64, io
import requests
from openai import OpenAI
from google import genai
from google.genai import types
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from dotenv import load_dotenv
from PIL import Image, ImageDraw, ImageFont
import json

# Provider 선택 (프론트 enum과 동일)
class Provider(str, Enum):
    GPT = "gpt"
    GEMINI = "gemini"
    MEME_GALTEYA = "meme_galteya"
    SNOW_NIGHT = "snow_night"
    PIXEL_ART = "pixel_art"
    AC_STYLE = "ac_style"

# 환경 변수 로딩
load_dotenv(os.getenv("ENV_PATH"))
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
OPENAI_BASE = os.getenv("OPENAI_BASE_URL", "")
GEMINI_BASE = os.getenv("GEMINI_BASE_URL", "")
OPENAI_IMAGE_MODEL = os.getenv("OPENAI_IMAGE_MODEL", "")
GEMINI_IMAGE_MODEL = os.getenv("GEMINI_IMAGE_MODEL", "")


# FastAPI 앱 및 CORS 설정
app = FastAPI(title="Image Proxy")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # TODO: 배포 시 도메인 제한
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==========================================
# 2. 공통 유틸 함수
# ==========================================

def _png_bytes(img_bytes: bytes) -> bytes:
    """임의 포맷 바이트를 PNG로 변환(일관성 보장). 실패 시 원본 반환."""
    ...

def _normalize_upload_image(upload: UploadFile):
    """
    업로드된 이미지를 읽어서:
      - 지원하지 않는 포맷이면 PNG로 변환
      - (바이트, mime, filename) 튜플로 반환
    """
    ...

def _http_err_from_requests(resp: requests.Response):
    """requests.Response를 HTTPException으로 변환 (디버그용 에러 메시지 포함)."""
    ...


# ==========================================
# 3. 스타일 프롬프트 헬퍼
#    (Provider별 스타일 설명을 프롬프트에 얹는 역할)
# ==========================================

def _style_prompt_meme_galteya(prompt: str) -> str:
    """갈테야테야 밈 스타일용 프롬프트 래핑."""
    ...

def _style_prompt_snow_night(prompt: str) -> str:
    """눈 내리는 밤 일러스트 스타일용 프롬프트 래핑."""
    ...

def _style_prompt_pixel_art(prompt: str) -> str:
    """픽셀 아트(16비트 게임) 스타일용 프롬프트 래핑."""
    ...

def _style_prompt_ac_style(prompt: str) -> str:
    """동물의 숲풍 카툰 스타일용 프롬프트 래핑."""
    ...


# ==========================================
# 4. 벤더 호출 함수 (실제 OpenAI/Gemini API 호출)
#    여기서는 "bytes"만 반환하고, Response는 엔드포인트에서 만든다.
# ==========================================

# ---------- 4-1. OpenAI / GPT-Image-1 계열 ----------

def _openai_text2image(prompt: str) -> bytes:
    """
    GPT-Image-1 text -> image
    - prompt를 받아 직접 API 호출
    - 반환: raw jpeg 이미지 바이트
    """

    # 사전 검증
    if not OPENAI_API_KEY:
        raise HTTPException(500, "OpenAI API key missing")
    if not OPENAI_IMAGE_MODEL:
        raise HTTPException(500, "OPENAI_IMAGE_MODEL is not set")
    
    client = OpenAI(api_key=OPENAI_API_KEY)

    resp = client.images.generate( prompt=prompt, model=OPENAI_IMAGE_MODEL, n=1, output_format="jpeg" )
    raw_bytes = base64.b64decode(resp.data[0].b64_json)
    return raw_bytes

def _openai_text_with_refs(
    prompt: str,
    images: List[UploadFile],
) -> bytes:
    """
    GPT-Image-1 text + reference images -> image
    - 업로드된 이미지를 참조로 쓰는 text2image
    """
    ...

def _openai_img_edit(
    prompt: str,
    base_image: UploadFile,
    mask_image: Optional[UploadFile] = None,
) -> bytes:
    """
    GPT-Image-1 이미지 편집 (image -> image / 인페인팅)
    - /images/edits 엔드포인트 사용
    """
    ...


# ---------- 4-2. Gemini 계열 (나중에 구현) ----------

def _gemini_text2image(prompt: str, images: Optional[List[UploadFile]]) -> bytes:
    """
    Gemini text -> image
    """

    # 사전 검증
    if not GEMINI_API_KEY:
        raise HTTPException(500, "Gemini API key missing")
    if not GEMINI_IMAGE_MODEL:
        raise HTTPException(500, "GEMINI_IMAGE_MODEL is not set")

    client = genai.Client(api_key=GEMINI_API_KEY)

    contents = [prompt]
    if images:
        for image in images:
            img_b = image.file.read()
            b64 = base64.b64encode(img_b).decode("utf-8")
            contents.append({
                "inlineData": {
                    "mimeType": "image/png",
                    "data": b64
                }
            })

    resp = client.models.generate_content(
        model=GEMINI_IMAGE_MODEL,
        contents=contents
    )

    for part in resp.parts:
        if part.text is not None:
            print(part.text)
        elif part.inline_data is not None:
            raw_bytes = part.inline_data.data
            return raw_bytes


def _gemini_img2img(
    prompt: str,
    images: List[UploadFile],
) -> bytes:
    """
    Gemini image -> image
    """
    ...


# ==========================================
# 5. 이미지 생성 엔드포인트 (+ provider별 분기까지 한 곳에서 처리)
# ==========================================

@app.post("/v1/images/generate")
async def generate_image(
    provider: Provider = Form(...),
    prompt: str = Form(...),
    images: Optional[List[UploadFile]] = File(None),
):
    """
    엔트리 포인트:
      1) provider별 동작 정의
      2) 벤더 헬퍼 호출
      3) bytes -> PNG로 변환 후 StreamingResponse 반환
    """
    try:
        # 1. provider별 동작 정의
        #    👉 지금은 GPT-Image-1만 먼저 제대로 붙이고,
        #       나중에 Gemini / 스타일 프리셋을 채워 넣는 방향으로.

        # ----- 기본 GPT provider -----
        if provider == Provider.GPT:
            img_bytes = _openai_text2image(prompt)
            return StreamingResponse(io.BytesIO(img_bytes), media_type="image/jpeg")

    

        # ----- 기본 Gemini provider -----
        elif provider == Provider.GEMINI:
            img_bytes = _gemini_text2image(prompt, images)
            return StreamingResponse(io.BytesIO(img_bytes), media_type="image/png")

        """
        # ----- 밈/스타일 provider들 (나중에 구현) -----
        elif provider == Provider.MEME_GALTEYA:
            # 1) 스타일 프롬프트 적용
            # 2) GPT provider 플로우를 재사용
            styled = _style_prompt_meme_galteya(prompt)
            # 여기서는 GPT text2image와 동일하게 동작시키거나,
            # 나중에 템플릿/인페인팅으로 변경 가능
            if mode == "text2image":
                if not images:
                    img_bytes = _openai_text2image(styled)
                else:
                    img_bytes = _openai_text_with_refs(styled, images)
            else:
                if not images:
                    raise HTTPException(400, "edit mode requires at least one image")
                base_image = images[0]
                img_bytes = _openai_img_edit(styled, base_image)

        elif provider == Provider.SNOW_NIGHT:
            # Gemini image -> image 전용으로 설계
            if not images:
                raise HTTPException(400, "snow_night requires at least one image")
            styled = _style_prompt_snow_night(prompt)
            img_bytes = _gemini_img2img(styled, images)

        elif provider == Provider.PIXEL_ART:
            # GPT image -> image (참조 이미지 필수)
            if not images:
                raise HTTPException(400, "pixel_art requires at least one image")
            styled = _style_prompt_pixel_art(prompt)
            img_bytes = _openai_text_with_refs(styled, images)

        elif provider == Provider.AC_STYLE:
            # GPT image -> image (참조 이미지 필수)
            if not images:
                raise HTTPException(400, "ac_style requires at least one image")
            styled = _style_prompt_ac_style(prompt)
            img_bytes = _openai_text_with_refs(styled, images)

        else:
            raise HTTPException(400, "unsupported provider")
        """
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==========================================
# 7. Meme Template Based Feature (기존 기능)
# ==========================================

# 템플릿 로드
# with open("templates/galteya.json", "r", encoding="utf-8") as f:
#     TEMPLATE_GALTEYA = json.load(f)

@app.get("/v1/templates")
def list_templates():
    """템플릿 목록 조회."""
    ...

@app.get("/v1/templates/{tid}")
def get_template(tid: str):
    """특정 템플릿 상세 조회."""
    ...

@app.post("/v1/memes/{tid}/generate")
async def generate_meme(
    tid: str,
    inputs: str = Form(...),
    files: List[UploadFile] = File(None),
):
    """
    템플릿 기반 밈 이미지 생성:
      - base_url 이미지를 가져와,
      - slots 정보에 따라 텍스트 합성,
      - inpaint 영역(mask) 모아서 _call_inpaint로 OpenAI 이미지 편집 요청.
    """
    ...


# ==========================================
# 8. 템플릿/인페인팅 내부 유틸
# ==========================================

def _draw_text(img, text, bbox, font_spec):
    """지정된 bbox 영역에 텍스트를 렌더링."""
    ...

def _merge_masks(mask_images):
    """여러 개의 마스크 이미지를 하나로 합성."""
    ...

def _call_inpaint(base_bytes, mask_bytes, prompt):
    """OpenAI /images/edits로 인페인팅 호출."""
    ...


@app.post("/api/meme_edit")
async def edit_meme_image(
    prompt: str = Form(...),
    base_image: UploadFile = File(...),
    mask_image: UploadFile = File(...)
):
    """테스트용 인페인팅 엔드포인트."""
    ...
