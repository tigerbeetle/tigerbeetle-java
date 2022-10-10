const std = @import("std");
const builtin = @import("builtin");

const assert = std.debug.assert;
const Atomic = std.atomic.Atomic;

// TODO: Reorganize tb_client to allow reference as a package instead of a static library
// Those decls should be avoided by using the tb_client.zig from tigerbeetle's submodule

pub const Client = *anyopaque;

pub const TBStatus = enum(c_int) {
    success = 0,
    unexpected,
    out_of_memory,
    invalid_address,
    system_resources,
    network_subsystem,
};

pub const CompletionFn = fn (
    context: usize,
    client: Client,
    packet: *Packet,
    result_ptr: ?[*]const u8,
    result_len: u32,
) callconv(.C) void;

pub extern fn tb_client_init(
    out_client: *Client,
    out_packets: *Packet.List,
    cluster_id: u32,
    addresses_ptr: [*:0]const u8,
    addresses_len: u32,
    num_packets: u32,
    on_completion_ctx: usize,
    on_completion_fn: CompletionFn,
) callconv(.C) TBStatus;

pub extern fn tb_client_submit(
    client: Client,
    packets: *Packet.List,
) callconv(.C) void;

pub extern fn tb_client_deinit(
    client: Client,
) callconv(.C) void;

pub const Packet = extern struct {
    next: ?*Packet,
    user_data: usize,
    operation: u8,
    status: Status,
    data_size: u32,
    data: [*]const u8,

    pub const Status = enum(u8) {
        ok,
        too_much_data,
        invalid_operation,
        invalid_data_size,
    };

    pub const List = extern struct {
        head: ?*Packet = null,
        tail: ?*Packet = null,

        pub fn from(packet: *Packet) List {
            packet.next = null;
            return List{
                .head = packet,
                .tail = packet,
            };
        }

        pub fn push(self: *List, packet: *Packet) void {
            const prev = if (self.tail) |tail| &tail.next else &self.head;
            prev.* = packet;
            self.tail = packet;
        }

        pub fn pop(self: *List) ?*Packet {
            const packet = self.head orelse return null;
            self.head = packet.next;
            if (self.head == null) self.tail = null;
            return packet;
        }
    };
};