const host = "https://minecraft.game-analytics.net/ctm";
let visibleThroughTerrain = true;

let button;
function createMenuButton() {
    button = document.createElement("div");
    button.className = "simple-button";
    button.style.cursor = "pointer";
    button.innerHTML = `<div class="label">Train Overlay</div><div class="submenu-icon"><svg viewBox="0 0 30 30"><path d="M25.004,9.294c0,0.806-0.75,1.46-1.676,1.46H6.671c-0.925,0-1.674-0.654-1.674-1.46l0,0
\tc0-0.807,0.749-1.461,1.674-1.461h16.657C24.254,7.833,25.004,8.487,25.004,9.294L25.004,9.294z"></path><path d="M25.004,20.706c0,0.807-0.75,1.461-1.676,1.461H6.671c-0.925,0-1.674-0.654-1.674-1.461l0,0
\tc0-0.807,0.749-1.461,1.674-1.461h16.657C24.254,19.245,25.004,19.899,25.004,20.706L25.004,20.706z"></path></svg></div>`;
    button.onclick = () => {
        const buttonList = document.querySelector(".side-menu .content")?.children.item(0);
        while (buttonList.firstChild) {
            buttonList.removeChild(buttonList.firstChild);
        }
        buttonList.appendChild(createButton("Toggle Visibility", () => { scene.visible = !scene.visible; }));
        buttonList.appendChild(createButton("Toggle visibility through terrain", () => { visibleThroughTerrain = !visibleThroughTerrain; }))
    };
}
function createButton(text, onclick = null) {
    const buttonTemplate = document.createElement("template");
    buttonTemplate.innerHTML = `
		<div class="simple-button">
			<div class="label">${text}</div>
		</div>
	`.trim();
    const button = buttonTemplate.content.firstChild;
    if (onclick) {
        button.onclick = onclick;
    }
    return button;
}
createMenuButton();
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function start() {
    await sleep(0); // sleep for 1 second

    fetchAndRenderNetwork();
    connectTrainStream();
    renderLoop();
}

start();
setInterval(() => {
    const buttonList = document.querySelector(".side-menu .content")?.children.item(0);
    if (!buttonList) return;

    if (!Array.from(buttonList.children).some(el => el === button)) {
        const infoButton = Array.from(buttonList.children).find(el => el.textContent.includes("Info"));
        if (infoButton && infoButton.nextSibling) {
            buttonList.insertBefore(button, infoButton.nextSibling);
        }
    }
}, 100);

//Network and rendering setup
const mapViewer = window.bluemap.mapViewer;
const renderer = mapViewer.renderer;
const THREE = window.BlueMap.Three;
const scene = new THREE.Scene();

// Line setup (prefer BlueMap's thick lines if available)
let LineMaterial = THREE.LineBasicMaterial,
    LineGeometry = THREE.BufferGeometry,
    LineClass = THREE.Line;
try {
    if (window.BlueMap?.LineMarker) {
        const marker = new window.BlueMap.LineMarker().line;
        LineClass = Object.getPrototypeOf(marker.constructor);
        LineMaterial = marker.material.constructor;
        LineGeometry = marker.geometry.constructor;
    }
} catch (_) { }

const resolution = new THREE.Vector2(window.innerWidth, window.innerHeight);
const lineMaterial = new LineMaterial({
    color: 0xffff00,
    linewidth: 2,
    resolution,
});
const portalMaterial = new LineMaterial({
    color: 0x00ffff,
    linewidth: 4,
    resolution,
});
const stationMaterial = new THREE.MeshBasicMaterial({ color: 0x00cc00 });
const stationGeometry = new THREE.SphereGeometry(1, 16, 16);

const trainGeometry = new THREE.BoxGeometry(5, 2, 2);
const trainMaterial = new THREE.MeshBasicMaterial({ color: 0x3366cc });

const objects = {
    tracks: new Map(),
    trains: new Map(),
    portals: new Map(),
    stations: new Map(),
};

let networkData = null;
let trainsData = [];
let lastTrainState = new Map();

function getCurrentWorldKey() {
    const mapName = mapViewer.map.data.name;
    const m = /\((?<name>.*)\)/.exec(mapName);
    return m ? m.groups.name.toLocaleLowerCase() : mapName.toLocaleLowerCase();
}
function getNodeMap(dimKey) {
    if (!networkData) return new Map();
    const nodes = Array.from(networkData.nodes).filter(
        n => n.dimensionLocationData.dimension.includes(dimKey)
    );
    return new Map(nodes.map(n => [n.id, n]));
}

function fetchAndRenderNetwork() {
    fetch(`${host}/network`)
        .then(resp => resp.json())
        .then(data => {
            networkData = data;
            renderTracks();
            renderStations();
            renderPortals();
        });
}

function renderTracks() {
    objects.tracks.forEach(obj => scene.remove(obj));
    objects.tracks.clear();
    if (!networkData) return;
    const dimKey = getCurrentWorldKey();
    const nodeMap = getNodeMap(dimKey);
    const edges = Array.from(networkData.edges).filter(
        e => nodeMap.has(e.node1) && nodeMap.has(e.node2)
    );
    edges.forEach(edge => {
        let points = [];
        if (edge.bezierConnection) {
            const bez = edge.bezierConnection;
            const steps = 32;
            for (let i = 0; i <= steps; i++) {
                const t = i / steps;
                const u = 1 - t;
                const pt = {
                    x: u ** 3 * bez.p0.x + 3 * u ** 2 * t * bez.p1.x + 3 * u * t ** 2 * bez.p2.x + t ** 3 * bez.p3.x,
                    y: u ** 3 * bez.p0.y + 3 * u ** 2 * t * bez.p1.y + 3 * u * t ** 2 * bez.p2.y + t ** 3 * bez.p3.y,
                    z: u ** 3 * bez.p0.z + 3 * u ** 2 * t * bez.p1.z + 3 * u * t ** 2 * bez.p2.z + t ** 3 * bez.p3.z,
                };
                points.push(pt);
            }
        } else {
            const n1 = nodeMap.get(edge.node1);
            const n2 = nodeMap.get(edge.node2);
            if (!n1 || !n2) return;
            const p1 = n1.dimensionLocationData.location;
            const p2 = n2.dimensionLocationData.location;
            points = [p1, p2];
        }
        let lineObj;
        if (LineGeometry === THREE.BufferGeometry) {
            const geometry = new LineGeometry();
            geometry.setFromPoints(points.map(pt => new THREE.Vector3(pt.x, pt.y, pt.z)));
            lineObj = new LineClass(geometry, lineMaterial);
        } else {
            const geometry = new LineGeometry();
            geometry.setPositions(points.flatMap(pt => [pt.x, pt.y, pt.z]));
            lineObj = new LineClass(geometry, lineMaterial);
        }
        scene.add(lineObj);
        objects.tracks.set(`${edge.node1}:${edge.node2}`, lineObj);
    });
}

function renderPortals() {
    objects.portals?.forEach(obj => scene.remove(obj));
    objects.portals = new Map();

    const dimKey = getCurrentWorldKey();
    let nodes = getNodeMap(dimKey);

    const portalGeometry = new THREE.SphereGeometry(1.2, 16, 16);
    const portalMaterial = new THREE.MeshBasicMaterial({ color: 0xbb00ff, emissive: 0x660099 });

    nodes.forEach(node => {
        if (node.interDimensional) {
            const pos = node.dimensionLocationData.location;
            const mesh = new THREE.Mesh(portalGeometry, portalMaterial);
            mesh.position.set(pos.x, pos.y + 2, pos.z);
            scene.add(mesh);
            objects.portals.set(node.id, mesh);
        }
    });
}

function renderStations() {
    objects.stations?.forEach(obj => scene.remove(obj));
    objects.stations = new Map();

    if (!networkData || !networkData.stations) return;
    const dimKey = getCurrentWorldKey();
    const nodeMap = getNodeMap(dimKey);

    networkData.stations.forEach(station => {
        const n1 = nodeMap.get(station.node1.id ?? station.node1);
        const n2 = nodeMap.get(station.node2.id ?? station.node2);
        if (!n1 || !n2) return;

        // Find edge
        const edge = networkData.edges.find(e =>
            (e.node1 === n1.id && e.node2 === n2.id) ||
            (e.node1 === n2.id && e.node2 === n1.id)
        );
        let pos;
        if (edge && edge.bezierConnection) {
            // Bezier
            const bez = edge.bezierConnection;
            const totalDist = approximateBezierLength(bez.p0, bez.p1, bez.p2, bez.p3);
            const t = Math.max(0, Math.min(1, (station.positionOnTrack || 0) / (totalDist || 1)));
            pos = cubicBezier3D(bez.p0, bez.p1, bez.p2, bez.p3, t);
        } else if (n1 && n2) {
            // Straight
            const p1 = n1.dimensionLocationData.location;
            const p2 = n2.dimensionLocationData.location;
            const dist = Math.sqrt(
                (p1.x - p2.x) ** 2 +
                (p1.y - p2.y) ** 2 +
                (p1.z - p2.z) ** 2
            );
            const t = Math.max(0, Math.min(1, (station.positionOnTrack || 0) / (dist || 1)));
            pos = {
                x: p1.x + (p2.x - p1.x) * t,
                y: p1.y + (p2.y - p1.y) * t,
                z: p1.z + (p2.z - p1.z) * t
            };
        }
        if (!pos) return;
        const mesh = new THREE.Mesh(stationGeometry, stationMaterial);
        mesh.position.set(pos.x, pos.y, pos.z);
        scene.add(mesh);
        objects.stations.set(station.id, mesh);
    });
}

function cubicBezier3D(p0, p1, p2, p3, t) {
    const u = 1 - t;
    return {
        x: u ** 3 * p0.x + 3 * u ** 2 * t * p1.x + 3 * u * t ** 2 * p2.x + t ** 3 * p3.x,
        y: u ** 3 * p0.y + 3 * u ** 2 * t * p1.y + 3 * u * t ** 2 * p2.y + t ** 3 * p3.y,
        z: u ** 3 * p0.z + 3 * u ** 2 * t * p1.z + 3 * u * t ** 2 * p2.z + t ** 3 * p3.z
    };
}
function approximateBezierLength(p0, p1, p2, p3, steps = 50) {
    let length = 0, prev = p0;
    for (let i = 1; i <= steps; i++) {
        const t = i / steps;
        const pt = cubicBezier3D(p0, p1, p2, p3, t);
        length += Math.sqrt((pt.x - prev.x) ** 2 + (pt.y - prev.y) ** 2 + (pt.z - prev.z) ** 2);
        prev = pt;
    }
    return length;
}

function findEdge(nodeA, nodeB) {
    if (!networkData) return null;
    return Array.from(networkData.edges).find(e =>
        (e.node1 === nodeA.id && e.node2 === nodeB.id) ||
        (e.node1 === nodeB.id && e.node2 === nodeA.id)
    );
}

function getPositionOnEdge(nodeA, nodeB, positionOnTrack, edge) {
    const p1 = nodeA.dimensionLocationData.location;
    const p2 = nodeB.dimensionLocationData.location;
    if (edge && edge.bezierConnection) {
        const bez = edge.bezierConnection;
        const reversed = nodeA.id === edge.node2 && nodeB.id === edge.node1;
        const bz = bez;
        const p0 = bz.p0, p1 = bz.p1, p2 = bz.p2, p3 = bz.p3;
        const totalDist = approximateBezierLength(p0, p1, p2, p3);
        const t = Math.max(0, Math.min(1, (positionOnTrack || 0) / (totalDist || 1)));
        const pt = reversed ? bezier3(p3, p2, p1, p0, t) : bezier3(p0, p1, p2, p3, t);
        return pt;
    }
    const lineLength = Math.sqrt(
        (p2.x - p1.x) ** 2 +
        (p2.y - p1.y) ** 2 +
        (p2.z - p1.z) ** 2
    );
    const t = Math.max(0, Math.min(1, (positionOnTrack || 0) / (lineLength || 1)));
    const pos = {
        x: p1.x + (p2.x - p1.x) * t,
        y: p1.y + (p2.y - p1.y) * t,
        z: p1.z + (p2.z - p1.z) * t
    };
    return pos;
}
function bezier3(p0, p1, p2, p3, t) {
    const u = 1 - t;
    return {
        x: u ** 3 * p0.x + 3 * u ** 2 * t * p1.x + 3 * u * t ** 2 * p2.x + t ** 3 * p3.x,
        y: u ** 3 * p0.y + 3 * u ** 2 * t * p1.y + 3 * u * t ** 2 * p2.y + t ** 3 * p3.y,
        z: u ** 3 * p0.z + 3 * u ** 2 * t * p1.z + 3 * u * t ** 2 * p2.z + t ** 3 * p3.z
    };
}
function bezierTangent3(p0, p1, p2, p3, t) {
    const u = 1 - t;
    return {
        x: 3 * u * u * (p1.x - p0.x) + 6 * u * t * (p2.x - p1.x) + 3 * t * t * (p3.x - p2.x),
        y: 3 * u * u * (p1.y - p0.y) + 6 * u * t * (p2.y - p1.y) + 3 * t * t * (p3.y - p2.y),
        z: 3 * u * u * (p1.z - p0.z) + 6 * u * t * (p2.z - p1.z) + 3 * t * t * (p3.z - p2.z)
    };
}

function orientTrainMesh(mesh, pos, tangent) {
    mesh.position.set(pos.x, pos.y + 1, pos.z);

    const forward = new THREE.Vector3(tangent.x, tangent.y, tangent.z).normalize();
    if (forward.length() < 1e-6) return;

    // World up (no rolling)
    const up = new THREE.Vector3(0, 1, 0);

    // Recompute a right vector orthogonal to forward & up
    const right = new THREE.Vector3().crossVectors(up, forward).normalize();

    // Recompute corrected up (to ensure orthogonality)
    const adjustedUp = new THREE.Vector3().crossVectors(forward, right).normalize();

    // Build rotation matrix from basis vectors
    const m = new THREE.Matrix4();
    m.makeBasis(right, adjustedUp, forward);

    // Apply quaternion
    mesh.quaternion.setFromRotationMatrix(m);
}

function interpolateCar(carState, now) {
    if (!carState) return {
        front: { pos: { x: 0, y: 0, z: 0 }, tangent: { x: 1, y: 0, z: 0 } },
        back: { pos: { x: 0, y: 0, z: 0 }, tangent: { x: 1, y: 0, z: 0 } }
    };

    const {
        startFront, endFront,
        startBack, endBack,
        startTime, endTime
    } = carState;

    if (!startFront || !endFront || !startBack || !endBack) {
        return {
            front: { pos: endFront || { x: 0, y: 0, z: 0 }, tangent: { x: 1, y: 0, z: 0 } },
            back: { pos: endBack || { x: 0, y: 0, z: 0 }, tangent: { x: 1, y: 0, z: 0 } }
        };
    }

    const t = Math.min(1, (now - startTime) / (endTime - startTime));

    const frontPos = {
        x: startFront.x + (endFront.x - startFront.x) * t,
        y: startFront.y + (endFront.y - startFront.y) * t,
        z: startFront.z + (endFront.z - startFront.z) * t
    };
    const backPos = {
        x: startBack.x + (endBack.x - startBack.x) * t,
        y: startBack.y + (endBack.y - startBack.y) * t,
        z: startBack.z + (endBack.z - startBack.z) * t
    };

    const tangent = {
        x: frontPos.x - backPos.x,
        y: frontPos.y - backPos.y,
        z: frontPos.z - backPos.z
    };

    return {
        pos: frontPos, tangent
    };
}

function connectTrainStream() {
    const eventSource = new EventSource(`${host}/trainsLive`);
    eventSource.onmessage = (e) => {
        trainsData = JSON.parse(e.data);
        if (trainModelCache === undefined) {
            trainModelCache = new Map();
            getTrainModels(trainsData);
        }
        updateTrainStates();
    };
    eventSource.onerror = (err) => {
        console.error("SSE error:", err);
    };
}

function updateTrainStates() {
    if (!networkData) return;
    const now = performance.now();
    const dimKey = getCurrentWorldKey();
    const nodeMap = getNodeMap(dimKey);

    trainsData.forEach(train => {
        if (!train.cars) return;
        const prevTrain = lastTrainState.get(train.id) || { cars: [] };
        const stateCars = [];

        train.cars.forEach((car, carIdx) => {
            // --- Leading point (front of car) ---
            const nodeA = nodeMap.get(car.node1);
            const nodeB = nodeMap.get(car.node2);
            if (!nodeA || !nodeB) return;
            const edgeFront = findEdge(nodeA, nodeB);
            const frontPos = getPositionOnEdge(
                nodeA, nodeB, car.positionOnTrack, edgeFront
            );

            // --- Trailing point (back of car) ---
            const nodeC = nodeMap.get(car.node3);
            const nodeD = nodeMap.get(car.node4);
            if (!nodeC || !nodeD) return;
            const edgeBack = findEdge(nodeC, nodeD);
            const backPos = getPositionOnEdge(
                nodeC, nodeD, car.trailingPositionOnTrack, edgeBack
            );

            // Handle interpolation with previous frame
            const prev = prevTrain.cars[carIdx];
            let startFront = frontPos;
            let endFront = frontPos;
            let startBack = backPos;
            let endBack = backPos;
            let startTime = now;
            let endTime = now;

            if (prev && prev.dimension === nodeA.dimensionLocationData.dimension) {
                startFront = prev.endFront || frontPos;
                endFront = frontPos;

                startBack = prev.endBack || backPos;
                endBack = backPos;

                startTime = now;
                endTime = now + 200;
            }

            stateCars.push({
                startFront, endFront,
                startBack, endBack,
                startTime, endTime,
                dimension: nodeA.dimensionLocationData.dimension
            });
        });

        lastTrainState.set(train.id, { cars: stateCars, time: now });
    });

    renderTrains();
}


function renderTrains() {
    objects.trains.forEach(obj => scene.remove(obj));
    objects.trains.clear();

    const dimKey = getCurrentWorldKey();
    if (!networkData || !trainsData || !lastTrainState.size) return;

    trainsData.forEach(train => {
        if (!train.cars) return;
        const state = lastTrainState.get(train.id);
        if (!state) return;
        state.cars.forEach((carState, carIdx) => {
            if (!carState) return;
            if (!carState.dimension.includes(dimKey)) return;

            // Example key, replace with your own logic for model selection
            const url = `${host}/trainModels/${train.id}_${carIdx}.prbm`;
            const model = trainModelCache.get(url);
            let mesh;

            if (model && model.geometry && model.material) {
                mesh = new THREE.Mesh(model.geometry, model.material);
            } else {
                mesh = new THREE.Mesh(
                    new THREE.BoxGeometry(5, 2, 2),
                    new THREE.MeshBasicMaterial({ color: 0x3366cc })
                );
            }
            scene.add(mesh);
            objects.trains.set(`${train.id}:${carIdx}`, mesh);
        });
    });
}

function animateTrains() {
    const now = performance.now();
    const dimKey = getCurrentWorldKey();

    trainsData.forEach(train => {
        if (!train.cars) return;
        const state = lastTrainState.get(train.id);
        if (!state) return;

        train.cars.forEach((car, carIdx) => {
            const carState = state.cars[carIdx];
            if (!carState) return;
            if (!carState.dimension.includes(dimKey)) return;
            const mesh = objects.trains.get(`${train.id}:${carIdx}`);
            if (!mesh) return;

            const { pos, tangent } = interpolateCar(carState, now);
            orientTrainMesh(mesh, pos, tangent);
        });
    });
}

let lastWorld = getCurrentWorldKey();
setInterval(() => {
    const current = getCurrentWorldKey();
    if (lastWorld !== current) {
        lastWorld = current;
        fetchAndRenderNetwork();
    }
}, 500);

window.addEventListener("resize", () => {
    if (lineMaterial.resolution) lineMaterial.resolution.set(window.innerWidth, window.innerHeight);
    if (portalMaterial.resolution) portalMaterial.resolution.set(window.innerWidth, window.innerHeight);
    lineMaterial.needsUpdate = true;
    portalMaterial.needsUpdate = true;
});

function renderLoop() {
    animateTrains();
    if (visibleThroughTerrain) {
        renderer.clearDepth();
    }
    renderer.render(scene, mapViewer.camera);
    requestAnimationFrame(renderLoop);
}

let trainModelCache;
async function loadTrainModelPRBM(url, direction) {
    if (trainModelCache.has(url)) return trainModelCache.get(url);

    try {
        const resp = await fetch(url);
        const arrayBuffer = await resp.arrayBuffer();
        let map;
        bluemap.maps.forEach(bluemapmap => {
            if (bluemapmap.hiresTileManager!=null) {
                map = bluemapmap;
                return;
            }
        });

        const loader = map.hiresTileManager.tileLoader.bufferGeometryLoader;
        const geometry = loader.parse(arrayBuffer);
        //rotate geometry based on assembly direction
        rotateGeometryToDirection(geometry, direction);

        // Pick material based on PRBM group or just first material
        let mat = map.hiresMaterial;
        if (!mat) {
            // fallback
            if (geometry.getAttribute("color")) {
                mat = new THREE.MeshStandardMaterial({ vertexColors: true, flatShading: true });
            } else {
                mat = new THREE.MeshStandardMaterial({ color: 0x3366cc, flatShading: true });
            }
        }
        const model = { geometry, material: mat };
        trainModelCache.set(url, model);
    } catch (e) {
        console.error("Failed to load PRBM train model:", e);
    }
}

async function getTrainModels(trainsData) {
    const urls = [];
    trainsData.forEach(train => {
        train.cars.forEach((car, carIdx) => {
            urls.push({ url: `${host}/trainModels/${train.id}_${carIdx}.prbm`, direction: car.assemblyDirection ?? "SOUTH" });
        });
    });
    await Promise.all(urls.map(({ url, direction }) => loadTrainModelPRBM(url, direction)));
}

function rotateGeometryToDirection(geometry, assemblyDirection, targetDirection = "NORTH") {
    const directionAngles = {
        "NORTH": 0,
        "EAST": -Math.PI / 2,
        "SOUTH": -Math.PI,
        "WEST": Math.PI / 2
    };

    const currentAngle = directionAngles[assemblyDirection] ?? 0;
    const targetAngle = directionAngles[targetDirection] ?? 0;
    const rotationAngle = targetAngle - currentAngle;

    geometry.applyMatrix4(new THREE.Matrix4().makeRotationY(rotationAngle));
    const offsets = {
        "NORTH": { x: -0.5, y: 0, z: -1.5 },
        "EAST": { x: -0.5, y: 0, z: -0.5 },
        "SOUTH": { x: 0.5, y: 0, z: -0.5 },
        "WEST": { x: 0.5, y: 0, z: -1.5 }
    };

    const offset = offsets[assemblyDirection] ?? { x: 0, y: 0, z: 0 };

    geometry.applyMatrix4(
        new THREE.Matrix4().makeTranslation(offset.x, offset.y, offset.z)
    );
}

Object.defineProperty(bluemap.mapViewer, "lastRedrawChange", {
    get: () => Date.now(),
    set: () => { },
});
