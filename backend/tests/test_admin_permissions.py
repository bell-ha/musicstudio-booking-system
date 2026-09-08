"""
관리자 전용 엔드포인트에 일반 사용자가 접근하면 403인지.
"""
from tests.conftest import future_date


def test_list_all_bookings_requires_admin(client, make_user, make_room, auth_headers):
    user = make_user()
    admin = make_user(role="admin")

    as_user = client.get("/api/bookings/", headers=auth_headers(user))
    assert as_user.status_code == 403

    as_admin = client.get("/api/bookings/", headers=auth_headers(admin))
    assert as_admin.status_code == 200


def test_create_admin_block_requires_admin(client, make_user, make_room, auth_headers):
    user = make_user()
    admin = make_user(role="admin")
    room = make_room()
    d = future_date()
    payload = {
        "room_id": room.room_id,
        "start_date": d.isoformat(),
        "end_date": d.isoformat(),
        "start_time": "09:00:00",
        "end_time": "10:00:00",
    }

    as_user = client.post("/api/admin/bookings/", headers=auth_headers(user), json=payload)
    assert as_user.status_code == 403

    as_admin = client.post("/api/admin/bookings/", headers=auth_headers(admin), json=payload)
    assert as_admin.status_code == 201, as_admin.text


def test_delete_admin_block_requires_admin(client, make_user, make_room, auth_headers):
    user = make_user()
    admin = make_user(role="admin")
    room = make_room()
    d = future_date()
    payload = {
        "room_id": room.room_id,
        "start_date": d.isoformat(),
        "end_date": d.isoformat(),
        "start_time": "09:00:00",
        "end_time": "10:00:00",
    }
    created = client.post("/api/admin/bookings/", headers=auth_headers(admin), json=payload)
    block_id = created.json()["booking_id"]

    as_user = client.delete(f"/api/admin/bookings/{block_id}", headers=auth_headers(user))
    assert as_user.status_code == 403

    as_admin = client.delete(f"/api/admin/bookings/{block_id}", headers=auth_headers(admin))
    assert as_admin.status_code == 204


def test_pending_user_cannot_login(client, make_user):
    pending = make_user(role="pending")
    res = client.post(
        "/auth/login",
        data={"username": pending.login_id, "password": pending.plain_password},
    )
    assert res.status_code == 403
