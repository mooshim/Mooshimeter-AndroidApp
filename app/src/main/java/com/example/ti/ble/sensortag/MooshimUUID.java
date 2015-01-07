package com.example.ti.ble.sensortag;

import java.util.UUID;

import static java.util.UUID.fromString;

/**
 * Created by First on 1/2/2015.
 */
public class MooshimUUID {
    private static final String uuid_base = "1bc50000-0200-62ab-e411-f254e005dbd4";
    public UUID
            UUID_SERV,
            UUID_DATA,
            UUID_CONF,
            UUID_PERI;
    public MooshimUUID(int short_uuid) {

    }
}
