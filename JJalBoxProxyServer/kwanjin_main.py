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
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from dotenv import load_dotenv
from PIL import Image, ImageDraw, ImageFont
import json
from IPython.display import Image as IPImage, display

# Provider 선택
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

# OpenAI 클라이언트 초기화 및 변수 설정
client = OpenAI(api_key=OPENAI_API_KEY)

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

def _http_err(resp: requests.Response):
    """외부 API 에러를 FastAPI HTTPException으로 변환."""
    ...

def _normalize_upload_image(upload: UploadFile):
    """
    업로드된 이미지를 읽어서:
      - 지원하지 않는 포맷이면 PNG로 변환
      - (바이트, mime, filename) 튜플로 반환
    """
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
# 4. 로우 레벨 벤더 호출 레이어
#    (OpenAI / Gemini 각각의 text2image, img2img 등)
# ==========================================

# ---------- 4-1. OpenAI 계열 ----------

def _openai_text2image(prompt: str, size: str) -> bytes:
    """
    OpenAI /images/generations (text -> image)
    - body: JSON
    - 반환: raw 이미지 바이트
    """
    img = client.images.generate(
        model=OPENAI_IMAGE_MODEL,
        prompt=prompt,
        n=1,
    )
    return base64.b64decode(img.data[0].b64_json)

def _openai_text_with_refs(
    prompt: str,
    size: str,
    images: List[UploadFile],
) -> bytes:
    """
    OpenAI /images/generations (text + image reference -> image)
    - body: multipart/form-data (model, prompt, size, image[])
    """
    ...

def _openai_img_edit(
    prompt: str,
    size: str,
    base_image: UploadFile,
    mask_image: Optional[UploadFile] = None,
) -> bytes:
    """
    OpenAI /images/edits (image -> image / 인페인팅)
    - base 이미지와 선택적 mask를 사용해 편집
    """
    ...


# ---------- 4-2. Gemini 계열 ----------

def _gemini_text2image(
    prompt: str,
    size: str,
    ref_images: Optional[List[UploadFile]] = None,
) -> bytes:
    """
    Gemini generateContent (text -> image, optional image reference)
    - contents.parts에 text + inlineData(image) 전달
    """
    ...

def _gemini_img2img(
    prompt: str,
    size: str,
    images: List[UploadFile],
) -> bytes:
    """
    Gemini generateContent (image -> image)
    - 최소 1장의 이미지를 받아 img2img처럼 처리
    """
    ...


# ==========================================
# 5. Provider 레벨 라우터
#    (비즈니스 의미에 따라 어떤 벤더/모드로 보낼지 결정)
# ==========================================

def _route_gpt(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == gpt 인 경우:
      - mode == "text2image":
          - 이미지 없음 -> _openai_text2image
          - 이미지 있음 -> _openai_text_with_refs (참조 이미지)
      - mode == "edit":
          - 첫 번째 이미지를 base로 _openai_img_edit
    """
    ...

def _route_gemini(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == gemini 인 경우:
      - mode == "text2image":
          - 이미지 유무와 관계없이 _gemini_text2image
      - mode == "edit":
          - 첫 번째 이미지를 사용해 _gemini_img2img
    """
    ...

def _route_meme_galteya(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == meme_galteya:
      - GPT 이미지 모델 사용
      - text / (text + images) -> 밈 스타일 프롬프트로 감싸서 생성
      - 내부적으로는 _route_gpt 재사용 가능
    """
    ...

def _route_snow_night(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == snow_night:
      - Gemini 기반 image -> image
      - 최소 1장의 이미지가 필수
      - 모드는 사실상 img2img이므로, mode 값에 상관 없이 _gemini_img2img 호출
    """
    ...

def _route_pixel_art(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == pixel_art:
      - GPT 이미지(gpt-image-1) 기반 image -> image
      - 최소 1장의 이미지가 필수
      - 스타일 프롬프트(_style_prompt_pixel_art)로 감싸고,
        OpenAI text+image reference 방식(_openai_text_with_refs) 사용
    """
    ...

def _route_ac_style(
    mode: str,
    prompt: str,
    size: str,
    images: Optional[List[UploadFile]],
) -> bytes:
    """
    provider == ac_style:
      - GPT 이미지(gpt-image-1) 기반 image -> image
      - 최소 1장의 이미지가 필수
      - 스타일 프롬프트(_style_prompt_ac_style)로 감싸고,
        OpenAI text+image reference 방식(_openai_text_with_refs) 사용
    """
    ...


# ==========================================
# 6. 이미지 생성 엔드포인트
# ==========================================

@app.post("/v1/images/generate")
async def generate_image(
    provider: Provider = Form(...),
    mode: str = Form(...),               # "text2image" | "edit"
    prompt: str = Form(...),
    size: str = Form("1024x1024"),
    images: Optional[List[UploadFile]] = File(None),
):
    """
    엔트리 포인트:
      1) mode 값 검증
      2) provider 별 라우터로 분기
      3) 최종적으로 bytes를 받아 PNG로 감싸 StreamingResponse 반환
    """
    try:
        # 1. mode 검증
        ...

        # 2. provider별 라우팅
        if provider == Provider.GPT:
            img_bytes = _route_gpt(mode, prompt, size, images)
        elif provider == Provider.GEMINI:
            img_bytes = _route_gemini(mode, prompt, size, images)
        elif provider == Provider.MEME_GALTEYA:
            img_bytes = _route_meme_galteya(mode, prompt, size, images)
        elif provider == Provider.SNOW_NIGHT:
            img_bytes = _route_snow_night(mode, prompt, size, images)
        elif provider == Provider.PIXEL_ART:
            img_bytes = _route_pixel_art(mode, prompt, size, images)
        elif provider == Provider.AC_STYLE:
            img_bytes = _route_ac_style(mode, prompt, size, images)
        else:
            raise HTTPException(400, "unsupported provider")

        # 3. 공통 응답: PNG로 반환
        png = _png_bytes(img_bytes)
        return StreamingResponse(io.BytesIO(png), media_type="image/png")

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==========================================
# 7. Meme Template Based Feature (기존 기능)
#    - 템플릿 JSON 로드
#    - /v1/templates, /v1/templates/{tid}
#    - /v1/memes/{tid}/generate
# ==========================================

# 템플릿 로드
with open("templates/galteya.json", "r", encoding="utf-8") as f:
    TEMPLATE_GALTEYA = json.load(f)

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

def _merge_masks(mask_images, size):
    """여러 개의 마스크 이미지를 하나로 합성."""
    ...

def _call_inpaint(base_bytes, mask_bytes, prompt, size):
    """OpenAI /images/edits로 인페인팅 호출."""
    ...


# 단일 base+mask 테스트용 엔드포인트 (기존 디버그용)
@app.post("/api/meme_edit")
async def edit_meme_image(
    prompt: str = Form(...),
    base_image: UploadFile = File(...),
    mask_image: UploadFile = File(...)
):
    """
    밈 원본(base_image)과 마스크(mask_image)를 이용해 이미지 인페인팅 수행 (테스트용).
    """
    ...
