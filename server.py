from flask import Flask, request, jsonify
import psycopg2
import json

app = Flask(__name__)

# ✅ RDS 접속 설정
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

# 1. 전체 짤 이미지 리스트 조회
@app.route("/images", methods=["GET"])
def get_all_images():
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, url, tag, text FROM jjal_metadata")
        rows = cur.fetchall()
        result = [
            {"id": r[0], "url": r[1], "tag": r[2], "text": r[3]}
            for r in rows
        ]
        cur.close()
        conn.close()
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# 2. 키워드 검색 (tag 또는 text)
@app.route("/images/search", methods=["GET"])
def search_images():
    keyword = request.args.get("query", "").strip()
    if not keyword:
        return jsonify({"error": "query is required"}), 400

    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(
            """
            SELECT id, url, tag, text FROM jjal_metadata
            WHERE EXISTS (
                SELECT 1 FROM jsonb_array_elements_text(tag) AS t
                WHERE t ILIKE %s
            )
            OR text ILIKE %s
            """,
            (f"%{keyword}%", f"%{keyword}%")
        )
        rows = cur.fetchall()
        result = [
            {"id": r[0], "url": r[1], "tag": r[2], "text": r[3]}
            for r in rows
        ]
        cur.close()
        conn.close()
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
