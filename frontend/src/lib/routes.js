const viewPaths = {
  dashboard: "/",
  explorer: "/blocks",
  wallet: "/wallets",
  mining: "/mining",
  peers: "/peers"
};

const pathViews = {
  "": "dashboard",
  blocks: "explorer",
  transactions: "explorer",
  wallets: "wallet",
  mining: "mining",
  peers: "peers"
};

function normalizeHash(hash) {
  return (hash || "#/")
    .replace(/^#/, "")
    .replace(/^\/?/, "/")
    .replace(/\/+$/, "") || "/";
}

function parseRoute(hash) {
  const path = normalizeHash(hash);
  const [section = "", rawId] = path.slice(1).split("/");
  const view = pathViews[section] || "dashboard";
  const detailType = ["blocks", "transactions", "wallets", "peers"].includes(section) && rawId ? section : null;
  return {
    view,
    detailType,
    detailId: detailType ? decodeURIComponent(rawId) : null
  };
}

function routeToHash(view) {
  return `#${viewPaths[view] || viewPaths.dashboard}`;
}

function detailHash(type, id) {
  return `#/${type}/${encodeURIComponent(id)}`;
}

export { detailHash, parseRoute, routeToHash };
