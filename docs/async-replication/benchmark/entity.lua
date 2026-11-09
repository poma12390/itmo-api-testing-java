local count = 0
local reads, writes = {}, {}
local mode

function init(args)
   mode = assert(args[1])
   local headers = { ["Content-Type"] = "application/octet-stream" }
   for i = 0, 127 do
      local path = "/v0/entity?id=key-" .. i
      reads[i] = wrk.format("GET", path)
      writes[i] = wrk.format("PUT", path, headers, string.rep("x", 256))
   end
end

function request()
   local key = count % 128
   local write = mode == "PUT" or (mode == "MIXED" and math.floor(count / 128) % 2 == 1)
   count = count + 1
   return write and writes[key] or reads[key]
end
