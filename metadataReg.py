# 사용 예시 : python3 metadataReg.py --folder "무도콘 수정본/" --tags 무도 무한도전

import boto3
import psycopg2
import json
import argparse

# S3 설정
BUCKET_NAME = "jjalbox-meme-image-storage"

# DB 설정
DB_HOST = "jjalbox-database.c5yc2uk4knc2.ap-southeast-2.rds.amazonaws.com"
DB_NAME = "postgres"
DB_USER = "postgres"
DB_PASS = "YOUR_DB_PASSWORD"
DB_PORT = 5432

def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASS,
        port=DB_PORT
    )

def get_existing_urls(conn):
    cur = conn.cursor()
    cur.execute("SELECT url FROM jjal_metadata")
    urls = set(row[0] for row in cur.fetchall())
    cur.close()
    return urls

def insert_metadata(conn, url, tags):
    cur = conn.cursor()
    text = ""  # 텍스트는 수동 또는 OCR로 채울 예정
    cur.execute(
        "INSERT INTO jjal_metadata (url, tag, text) VALUES (%s, %s::jsonb, %s)",
        (url, json.dumps(tags), text)
    )
    conn.commit()
    cur.close()

def register_images(folder: str, tags: list[str]):
    s3 = boto3.client("s3")
    prefix = folder.strip("/") + "/"  # 예: "happy" → "happy/"

    response = s3.list_objects_v2(Bucket=BUCKET_NAME, Prefix=prefix)

    if "Contents" not in response:
        print(f"No images found in folder: {prefix}")
        return

    conn = get_db_connection()
    existing_urls = get_existing_urls(conn)

    for obj in response["Contents"]:
        key = obj["Key"]
        if key.endswith((".gif", ".png")):
            url = f"https://{BUCKET_NAME}.s3.ap-southeast-2.amazonaws.com/{key}"
            if url not in existing_urls:
                print(f"Inserting: {url}")
                insert_metadata(conn, url, tags)

    conn.close()

def parse_args():
    parser = argparse.ArgumentParser(description="Register jjal metadata from specific S3 folder")
    parser.add_argument("--folder", required=True, help="S3 하위 폴더 이름 (예: happy/)")
    parser.add_argument("--tags", nargs="+", required=True, help="해당 폴더 이미지에 적용할 공통 태그")
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_args()
    register_images(args.folder, args.tags)
